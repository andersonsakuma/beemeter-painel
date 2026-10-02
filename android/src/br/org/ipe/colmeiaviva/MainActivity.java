package br.org.ipe.colmeiaviva;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/** Casca nativa mínima: carrega o painel empacotado em assets/ dentro de um WebView, sem rede. */
public class MainActivity extends Activity {
    private static final int PEDIR_ARQUIVO = 1;
    private WebView web;
    private ValueCallback<Uri[]> retorno;
    private NosWifi nos;
    private Runnable pendente;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#EEF2EC"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage guarda os registros
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " ColmeiaVivaApp");

        nos = new NosWifi(this, new NosWifi.Saida() {
            public void emitir(JSONObject e) {
                final String js = "window.ColmeiaNos&&window.ColmeiaNos.evento(" + e.toString() + ")";
                runOnUiThread(new Runnable() { public void run() { web.evaluateJavascript(js, null); } });
            }
        });
        web.addJavascriptInterface(new Ponte(), "ColmeiaApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
                return !r.getUrl().toString().startsWith("file:///android_asset/");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (retorno != null) retorno.onReceiveValue(null);
                retorno = cb;
                try {
                    startActivityForResult(p.createIntent(), PEDIR_ARQUIVO);
                } catch (Exception e) {
                    retorno = null;
                    return false;
                }
                return true;
            }
        });

        if (b != null) web.restoreState(b);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == PEDIR_ARQUIVO && retorno != null) {
            retorno.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            retorno = null;
        }
    }

    private static final int PEDIR_PERMISSAO = 2;

    /** Busca de redes pede NEARBY_WIFI_DEVICES (Android 13+) ou localização (até o 12). */
    private void comPermissao(final Runnable acao) {
        final String perm = Build.VERSION.SDK_INT >= 33
                ? "android.permission.NEARBY_WIFI_DEVICES" : "android.permission.ACCESS_FINE_LOCATION";
        runOnUiThread(new Runnable() {
            public void run() {
                if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) { acao.run(); return; }
                pendente = acao;
                requestPermissions(new String[]{perm}, PEDIR_PERMISSAO);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req != PEDIR_PERMISSAO) return;
        Runnable acao = pendente;
        pendente = null;
        if (acao != null && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) acao.run();
        else nos.erro("Permissão negada: sem ela o Android não deixa o app procurar os nós.");
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        web.saveState(b);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Ponte para o JavaScript do painel: o WebView não baixa blobs, então o backup sai pelo menu Compartilhar. */
    private class Ponte {
        @JavascriptInterface
        public void buscarNos(final String prefixo) {
            comPermissao(new Runnable() { public void run() { nos.buscar(prefixo); } });
        }

        @JavascriptInterface
        public void lerNos(final String ssidsJson, final String senha) {
            comPermissao(new Runnable() { public void run() { nos.ler(ssidsJson, senha); } });
        }

        @JavascriptInterface
        public void cancelarNos() { nos.cancelar(); }

        @JavascriptInterface
        public void compartilhar(String texto) {
            final String t = texto;
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, "Colmeia Viva — backup dos registros");
                    i.putExtra(Intent.EXTRA_TEXT, t);
                    startActivity(Intent.createChooser(i, "Enviar backup"));
                }
            });
        }
    }
}
