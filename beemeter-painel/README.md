# Colmeia Viva — painel do BeeMeter

Painel de monitoramento e gestão de apiário do projeto **BeeMeter** (IPÊ/FIS — Amazônia Bee).

Roda em <https://andersonsakuma.github.io/beemeter-painel/>

## Por que este repositório existe separado

O projeto completo do BeeMeter — firmware, geradores da placa, Gerber, documento
técnico, manual de montagem — fica num repositório **privado**. Publicar o Pages a
partir dele exigiria plano pago, e mesmo assim o site sairia público.

Então aqui mora **somente o painel**: um arquivo HTML, sem nada do projeto de
hardware. O repositório é público, o Pages é gratuito, e nenhum Gerber, documento
ou preço fica exposto.

## Como publicar

Settings → Pages → **Source: Deploy from a branch** → `main` / `/ (root)` → Save.

Não há fluxo de Actions: a página é um arquivo único servido direto da raiz.

## Sobre os dados

As leituras dos sensores são **simuladas** — nenhum nó foi instalado em campo.

O cadastro de colmeias, inspeções e aferições é real e fica no `localStorage` do
navegador. Não há servidor: cada navegador guarda os próprios registros, e o que
for cadastrado num computador não aparece em outro. A aba **Gestão** tem
exportação e importação em JSON para levar os registros de uma máquina a outra.
A importação apenas **acrescenta** — nunca apaga o que já existe.

## Atualizar o painel

O arquivo nasce de `sistema/colmeia_viva.html` no repositório principal. Para
atualizar, regere `site/index.html` lá e copie o arquivo para cá.

---

Anderson Cardoso Sakuma — **IPÊ/FIS**, Projeto Amazônia Bee
