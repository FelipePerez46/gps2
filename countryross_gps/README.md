# CountryRoss GPS (Forge 1.20.1)

Item "CountryRoss GPS": clique direito abre o aparelho (animacao de abertura, busca de satelites, sons) com um mapa
verde de curvas de nivel. O mapa comeca vazio e revela o terreno em volta do jogador conforme ele anda
(estilo Xaero's World Map), so enquanto ele carrega o GPS. Fora do Overworld o aparelho mostra "SEM SINAL".

## Configuracao
- Servidor (vale para todos, sincronizado): `world/serverconfig/countryross_gps-server.toml`
  - `revealRadius` (8-128, padrao 64): quanto terreno e revelado ao redor do jogador. Menor = menos visivel.
  - `surfaceOnly`: nao mapeia enquanto o jogador esta em cavernas/minas.
  - `requireItem`: so mapeia com o GPS no inventario.
  Para ja nascer com seus valores em mundos novos, copie o arquivo para a pasta `defaultconfigs` do servidor.
- Jogador: `config/countryross_gps-client.toml` (animacao de abertura, radar, sons e volume, zoom inicial, grade...).

O mapa descoberto de cada jogador fica em `<minecraft>/countryross_gps/maps/`. Apagar o arquivo reinicia o mapa.

Dar o item: /give @p countryross_gps:gps

## Idiomas
Ingles (en_us) e portugues (pt_br / pt_pt). O mod usa o idioma escolhido no Minecraft.

## Gerar o .jar
GitHub: envie os arquivos, aba Actions > Build mod > Run workflow > baixe em Artifacts.
Local: JDK 17 + Gradle 8.1.1 -> `gradle build` -> `build/libs/countryross_gps-1.0.1.jar`.
