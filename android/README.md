# Colmeia Viva — aplicativo Android

O mesmo `index.html` do painel, empacotado num APK (WebView, funciona sem internet).

## Instalar

Escaneie com a câmera do celular:

![QR de instalação](qr-instalacao.png)

Link direto: <https://github.com/andersonsakuma/beemeter-painel/releases/latest/download/colmeia-viva.apk>

1. Abra o link e baixe o `colmeia-viva.apk`.
2. Toque no arquivo baixado. Na primeira vez o Android pede para permitir a instalação de apps desta origem (navegador ou gerenciador de arquivos) — permita.
3. Abra **Colmeia Viva**.

Requer Android 7.0 (API 24) ou superior. A partir da versão 1.1 o app pede a permissão *Dispositivos por perto* (Android 13+) ou *Localização* (até o 12), só para procurar os nós por Wi-Fi. A internet não é usada: o painel roda offline e o único tráfego é HTTP para o ponto de acesso do nó (`192.168.4.1`).

## Adicionar nós ESP32 (versão 1.1)

Em **Gestão → Nós BeeMeter próximos**: digite a senha do Wi-Fi dos nós, toque em **Buscar nós**, marque um ou vários e toque em **Adicionar e ler**. O app cadastra cada nó como colmeia (`BM-04` vira `COL-04`) e guarda a leitura, que aparece em **Painel → Nós reais**.

Esse fluxo depende de o firmware abrir o Wi-Fi `BM-NN` e responder em `/telemetria`: veja [PROTOCOLO-NOS.md](PROTOCOLO-NOS.md). O firmware atual **ainda não faz isso**, e a função não foi testada com um ESP32 real.

## Dados

As leituras da lista **Nós do apiário** continuam **simuladas**; só os cartões de **Nós reais** vêm de um ESP32. Colmeias, inspeções, aferições e leituras reais ficam no armazenamento do aplicativo, só naquele aparelho. Desinstalar o app apaga os registros: use **Gestão → Exportar tudo → Compartilhar** antes.

## Gerar o APK

Sem Gradle: `aapt2` + `javac` + `d8` + `apksigner`.

```bash
export JAVA_HOME="<JDK 11 ou superior>"
export KEYSTORE="<caminho do .jks>" KSPASS="<senha>"
bash android/build.sh        # saída: android/build/colmeia-viva.apk
```

O build copia o `../index.html` para os assets, então o painel web continua sendo a única fonte.
O keystore fica fora do repositório. Atualizações precisam ser assinadas com a **mesma chave**, senão o Android recusa a instalação por cima; aumente `VERSION_CODE`.
