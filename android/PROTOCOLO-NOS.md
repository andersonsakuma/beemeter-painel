# Protocolo de descoberta e leitura dos nós BeeMeter (Android)

O aplicativo encontra e lê os ESP32 pelo **Wi-Fi do próprio nó**, porque o ESP-NOW que o firmware usa hoje
não pode ser recebido por um celular. Este documento é o que o firmware precisa cumprir.
**O firmware atual não faz isso ainda**: é uma especificação, com um sketch de referência em
[`firmware-exemplo/bm_softap.ino`](firmware-exemplo/bm_softap.ino).

## Descoberta

- O nó abre um ponto de acesso (SoftAP) com SSID **`BM-NN`** (`BM-01`, `BM-02`…). O prefixo `BM-` é o que o app procura.
- O código da colmeia é derivado do SSID: `BM-04` vira `COL-04` no cadastro.
- Segurança: WPA2 com uma senha de 8 a 63 caracteres, igual em todos os nós e digitada uma vez no app
  (campo **Senha do Wi-Fi dos nós**). Sem senha, a rede é aberta.
- Canal fixo e `max_connection = 1` bastam.

## Leitura

O celular conecta ao nó e faz:

```
GET http://192.168.4.1/telemetria        (IP padrão do SoftAP do ESP32)
```

Resposta `200`, `Content-Type: application/json`, no máximo 4 KB. **Os campos são os do pacote de telemetria
de 36 bytes do BeeMeter** (aba Referência do painel), nas mesmas unidades:

| Campo | Tipo | Unidade | Sem leitura |
|---|---|---|---|
| `no` | string | `BM-04` | — |
| `fw` | string | versão do firmware | — |
| `seq` | int | contador do pacote | — |
| `tc1`, `tc2`, `tc3` | int | **centésimo de °C** (`3455` = 34,55 °C) | `-32768` ou `null` |
| `lux` | int | lux | `65535` ou `null` |
| `t_ext` | int | °C | `-32768` ou `null` |
| `rh` | int | % UR | `255` ou `null` |
| `peso` | int | grama | `-2147483648` ou `null` |
| `vbat` | int | milivolt | `0` ou `null` |
| `falhas`, `eventos` | int | máscara de bits | — |

Exemplo:

```json
{"no":"BM-04","fw":"2.0.0","seq":812,"tc1":3455,"tc2":3120,"tc3":2980,
 "lux":21000,"t_ext":27,"rh":61,"peso":null,"vbat":3980,"falhas":0,"eventos":0}
```

Campo vazio **nunca** vai como zero (regra do pacote). O app converte `tc*` para °C e trata os marcadores acima como "sem leitura".

## O nó precisa estar acordado

O nó dorme em ciclos de 5 minutos e fica acordado cerca de 2,6 s. Nesse tempo o celular não consegue
achá-lo, conectar e ler. O firmware precisa de um **modo de aproximação**: ao ser acionado (por exemplo,
abrir a tampa e acionar o reed switch, ou segurar um botão), o nó mantém o SoftAP ligado por
cerca de 2 a 5 minutos e só então volta a dormir. O sketch de exemplo mostra esse janelamento.

## O que o Android impõe

- **Uma rede por vez.** A busca lista vários nós ao mesmo tempo, mas a leitura percorre os escolhidos em fila:
  conecta, lê, desconecta, próximo. Ler N nós leva cerca de N × (5 a 15 s).
- **Confirmação do usuário.** A conexão a uma rede específica pede um aviso do Android; ele pode aparecer a cada nó.
- **Android 10 ou superior** para ler a telemetria (a busca funciona em versões anteriores).
- **Permissões:** *Dispositivos por perto* (Android 13+) ou *Localização* (até o Android 12). Em versões até
  o 12, o serviço de localização do aparelho também precisa estar ligado. O app não usa a localização para mais nada.
- O Android limita as buscas de Wi-Fi a poucas por minuto; se atingir o limite, o app mostra o último resultado.
