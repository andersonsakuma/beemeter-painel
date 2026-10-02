# Colmeia Viva — aplicativo Android

O mesmo `index.html` do painel, empacotado num APK (WebView, funciona sem internet).

## Instalar

Escaneie com a câmera do celular:

![QR de instalação](qr-instalacao.png)

Link direto: <https://github.com/andersonsakuma/beemeter-painel/releases/latest/download/colmeia-viva.apk>

1. Abra o link e baixe o `colmeia-viva.apk`.
2. Toque no arquivo baixado. Na primeira vez o Android pede para permitir a instalação de apps desta origem (navegador ou gerenciador de arquivos) — permita.
3. Abra **Colmeia Viva**.

Requer Android 7.0 (API 24) ou superior. O APK não pede nenhuma permissão e não usa rede.

## Dados

As leituras dos sensores continuam **simuladas**. Colmeias, inspeções e aferições ficam no armazenamento do aplicativo, só naquele aparelho. Desinstalar o app apaga os registros: use **Gestão → Exportar tudo → Compartilhar** antes.

## Gerar o APK

Sem Gradle: `aapt2` + `javac` + `d8` + `apksigner`.

```bash
export JAVA_HOME="<JDK 11 ou superior>"
export KEYSTORE="<caminho do .jks>" KSPASS="<senha>"
bash android/build.sh        # saída: android/build/colmeia-viva.apk
```

O build copia o `../index.html` para os assets, então o painel web continua sendo a única fonte.
O keystore fica fora do repositório. Atualizações precisam ser assinadas com a **mesma chave**, senão o Android recusa a instalação por cima; aumente `VERSION_CODE`.
