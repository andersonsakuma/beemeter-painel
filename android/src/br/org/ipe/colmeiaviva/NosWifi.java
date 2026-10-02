package br.org.ipe.colmeiaviva;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Descobre os nós BeeMeter (ESP32 em modo ponto de acesso, SSID "BM-xx") e lê a telemetria de cada um.
 * O Android só conecta a uma rede por vez: a busca lista vários nós ao mesmo tempo, a leitura percorre
 * os escolhidos em fila. Protocolo do nó: veja android/PROTOCOLO-NOS.md.
 */
class NosWifi {
    interface Saida { void emitir(JSONObject evento); }

    static final String IP_NO = "192.168.4.1";
    static final String CAMINHO = "/telemetria";

    private final Context ctx;
    private final Saida saida;
    private final WifiManager wifi;
    private final ConnectivityManager cm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BroadcastReceiver rx;
    private Runnable fimBusca;
    private ExecutorService fila;
    private volatile boolean cancelado;

    NosWifi(Context c, Saida s) {
        ctx = c.getApplicationContext();
        saida = s;
        wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private void emitir(String tipo, String chave, Object valor) {
        try {
            JSONObject o = new JSONObject().put("tipo", tipo);
            if (chave != null) o.put(chave, valor);
            saida.emitir(o);
        } catch (Exception ignorado) { }
    }

    void erro(String msg) { emitir("erro", "msg", msg); }

    /** Procura redes cujo nome começa com o prefixo (padrão "BM-"). */
    void buscar(String prefixo) {
        if (!wifi.isWifiEnabled()) { erro("O Wi-Fi está desligado. Ligue o Wi-Fi e busque de novo."); return; }
        final String pre = (prefixo == null || prefixo.isEmpty()) ? "BM-" : prefixo;
        pararBusca();
        rx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { publicar(pre); }
        };
        ctx.registerReceiver(rx, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        publicar(pre);                                   // o que o sistema já tinha em cache
        boolean iniciou = false;
        try { iniciou = wifi.startScan(); } catch (SecurityException e) { erro("Sem permissão para buscar redes."); }
        if (!iniciou) emitir("aviso", "msg", "O Android limita a frequência das buscas; mostrando o último resultado. Tente de novo em instantes.");
        fimBusca = new Runnable() { public void run() { pararBusca(); emitir("fimbusca", null, null); } };
        main.postDelayed(fimBusca, 12000);
    }

    private void pararBusca() {
        if (fimBusca != null) { main.removeCallbacks(fimBusca); fimBusca = null; }
        if (rx != null) { try { ctx.unregisterReceiver(rx); } catch (Exception ignorado) { } rx = null; }
    }

    private void publicar(String pre) {
        Map<String, Integer> melhor = new HashMap<>();
        List<ScanResult> rs;
        try { rs = wifi.getScanResults(); } catch (SecurityException e) { return; }
        for (ScanResult r : rs) {
            String s = r.SSID;
            if (s == null || !s.toUpperCase().startsWith(pre.toUpperCase())) continue;
            Integer atual = melhor.get(s);
            if (atual == null || r.level > atual) melhor.put(s, r.level);
        }
        try {
            JSONArray nos = new JSONArray();
            for (Map.Entry<String, Integer> e : melhor.entrySet())
                nos.put(new JSONObject().put("ssid", e.getKey()).put("rssi", e.getValue()));
            emitir("busca", "nos", nos);
        } catch (Exception ignorado) { }
    }

    /** Conecta em cada nó, um depois do outro, lê a telemetria e desconecta. */
    void ler(String ssidsJson, final String senha) {
        if (Build.VERSION.SDK_INT < 29) { erro("Ler a telemetria exige Android 10 ou superior."); return; }
        final List<String> lista = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(ssidsJson);
            for (int i = 0; i < a.length(); i++) lista.add(a.getString(i));
        } catch (Exception e) { erro("Lista de nós inválida."); return; }
        pararBusca();
        cancelado = false;
        if (fila != null) fila.shutdownNow();
        fila = Executors.newSingleThreadExecutor();
        fila.execute(new Runnable() {
            public void run() {
                for (String s : lista) { if (cancelado) break; lerUm(s, senha); }
                emitir("fim", null, null);
            }
        });
    }

    void cancelar() {
        cancelado = true;
        pararBusca();
    }

    private void progresso(String ssid, String etapa) {
        try { saida.emitir(new JSONObject().put("tipo", "progresso").put("ssid", ssid).put("etapa", etapa)); }
        catch (Exception ignorado) { }
    }

    private void resultado(String ssid, JSONObject dados, String falha) {
        try {
            JSONObject o = new JSONObject().put("tipo", "resultado").put("ssid", ssid).put("ok", dados != null);
            if (dados != null) o.put("dados", dados); else o.put("erro", falha);
            saida.emitir(o);
        } catch (Exception ignorado) { }
    }

    private void lerUm(final String ssid, String senha) {
        progresso(ssid, "conectando");
        WifiNetworkSpecifier.Builder b = new WifiNetworkSpecifier.Builder().setSsid(ssid);
        if (senha != null && senha.length() >= 8) b.setWpa2Passphrase(senha);
        NetworkRequest rq = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(b.build()).build();

        final CountDownLatch pronto = new CountDownLatch(1);
        final String[] corpo = new String[1];
        final String[] falha = new String[1];
        ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(final Network n) {
                progresso(ssid, "lendo");
                new Thread(new Runnable() {
                    public void run() {
                        try { corpo[0] = http(n); }
                        catch (Exception e) { falha[0] = "Conectou, mas não respondeu: " + e.getClass().getSimpleName(); }
                        pronto.countDown();
                    }
                }).start();
            }
            @Override public void onUnavailable() {
                falha[0] = "Não conectou (recusado, senha incorreta ou fora de alcance).";
                pronto.countDown();
            }
        };
        try {
            cm.requestNetwork(rq, cb, 30000);
            pronto.await(45, TimeUnit.SECONDS);
        } catch (Exception e) {
            falha[0] = "Erro ao conectar: " + e.getClass().getSimpleName();
        } finally {
            try { cm.unregisterNetworkCallback(cb); } catch (Exception ignorado) { }
        }
        if (corpo[0] != null) {
            try { resultado(ssid, new JSONObject(corpo[0]), null); }
            catch (Exception e) { resultado(ssid, null, "O nó respondeu algo que não é JSON."); }
        } else {
            resultado(ssid, null, falha[0] != null ? falha[0] : "Sem resposta.");
        }
    }

    private String http(Network n) throws Exception {
        HttpURLConnection c = (HttpURLConnection) n.openConnection(new URL("http://" + IP_NO + CAMINHO));
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[512]; int k;
            while ((k = in.read(buf)) > 0 && bo.size() < 4096) bo.write(buf, 0, k);
            return bo.toString("UTF-8");
        } finally { c.disconnect(); }
    }
}
