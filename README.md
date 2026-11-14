# Polícia e Ladrão — Assalto ao Banco

Jogo para dois jogadores ambientado em um assalto a banco, desenvolvido em **Java 17+** com interface gráfica (Swing). Um jogador é o **Ladrão**, que precisa encontrar o cofre verdadeiro, pegá-lo e fugir pela saída. O outro é o **Guarda**, que precisa capturá-lo antes.

O jogo roda como **três processos**: um servidor que guarda o estado verdadeiro da partida e dois clientes (um por jogador) que desenham o mapa e enviam comandos. Tudo se comunica por **sockets TCP** com mensagens de texto, uma por linha.

---

## Como jogar

### Rodada rápida (teste local)

1. dê dois cliques em **`run-local-demo.bat`**
2. vão abrir o servidor e **duas janelas de jogo**
3. em cada janela, deixe o endereço `127.0.0.1`, digite um nome e clique em conectar
4. o primeiro a conectar é o **Ladrão**; o segundo é o **Guarda**

### Partida normal

1. rode **`run-server.bat`** no computador que vai hospedar (porta **5050**)
2. rode **`policia-e-ladrao.jar`** duas vezes — cada janela é um jogador
   (pode ser no mesmo PC ou em PCs diferentes da mesma rede)
3. em cada janela, informe o IP do computador servidor (`127.0.0.1` se for o mesmo PC)

> O modo demo acelera tudo (tick de 0,5s e ciclos curtos), bom para testar e gravar vídeo. O modo normal usa os tempos do projeto: 1 tick = 1 segundo.

### Controles

| Ação | Ladrão | Guarda |
|---|---|---|
| Mover | W A S D ou setas | W A S D ou setas |
| Poder | **ESPAÇO** — Manto de invisibilidade | **F** — Armar alarme |
| Alternativa | Botões na tela ou clique numa casa vizinha | idem |

---

## Regras do jogo

- **Mapa 7×7 simétrico**: o Ladrão começa embaixo à esquerda, o Guarda em cima à direita e a saída embaixo à direita. Paredes e saída são sempre visíveis; o resto do mapa cada jogador só descobre explorando.
- **Movimento por turnos**: depois que um jogador anda uma casa, a vez passa para o outro. O painel mostra "SUA VEZ" / "VEZ DO OPONENTE" e ignora comandos fora de vez.
- **Visão limitada**: o Ladrão enxerga até 2 casas, o Guarda só 1. Casas descobertas ficam memorizadas na tela de cada um.
- **Cofre verdadeiro e isca**: há dois cofres iguais; só um é verdadeiro. O falso dispara um alarme regional e atordoa o Ladrão por 2 ticks.
- **Ciclos do cofre**: a cada ciclo o alarme geral toca, a porta fecha e os cofres mudam de lugar. Se o Ladrão não pegar o cofre a tempo, **a saída também muda de lugar** alguns instantes depois. Não há tempo limite de partida.
- **Manto de invisibilidade** (Ladrão, 1 uso): o Guarda não vê o Ladrão por 2 movimentos, nem encostado, e o Ladrão não deixa pegadas. A nuvem só aparece na tela do Ladrão.
- **Alarme** (Guarda, 2 usos, até 2 ativos): armado no piso do Guarda. Se o Ladrão pisar nele, o alarme é consumido e o Guarda vê a **posição exata** do Ladrão por 8 ticks.
- **Pegadas**: o Ladrão deixa rastros de direção quando está a 4+ casas do Guarda; o Guarda os vê a até 3 casas de distância.
- **Fim de jogo**: o Guarda vence capturando o Ladrão (ou se ele desconectar); o Ladrão vence pegando o cofre verdadeiro e alcançando a saída.

Os valores de tempo e equilíbrio ficam centralizados em `GameConfig.java`: `standard()` usa os tempos do projeto e `demo()` encurta tudo para testes.

---

## Como foi desenvolvido

### Arquitetura cliente-servidor

```text
┌─────────────┐         ┌─────────────┐
│ Cliente 1   │         │ Cliente 2   │
│ (Ladrão)    │         │ (Guarda)    │
└──────┬──────┘         └──────┬──────┘
       │   TCP :5050, texto    │
       └───────────┬───────────┘
                   ▼
            ┌─────────────┐
            │   Servidor  │
            │ GameEngine  │
            └─────────────┘
```

- **`GameServer`** aceita dois sockets, atribui os papéis (1º = Ladrão, 2º = Guarda) e mantém uma thread leitora por jogador.
- **`GameEngine`** é o estado verdadeiro do jogo. Todas as threads (jogadores, relógio e faxina) acessam o mesmo objeto protegido por **um único `ReentrantLock`** — isso é a exclusão mútua do trabalho: nenhum comando ou leitura do relógio acontece ao mesmo tempo.
- **Relógio**: uma thread agendada avança os ticks; outra remove pegadas e alarmes vencidos, ambas pelo mesmo mutex.
- **Visão filtrada**: o servidor nunca manda o mapa completo. Para cada jogador ele monta um `GameSnapshot` diferente, omitindo o adversário, cofres e rastros que aquele papel não pode ver. O cliente guarda só a última visão recebida.

### Protocolo (texto, um comando por linha)

| Mensagem | Significado |
|---|---|
| `L:M:N/S/L/O`, `G:M:N/S/L/O` | Ladrão/Guarda move ao norte, sul, leste ou oeste |
| `L:X` | Ladrão usa o manto |
| `G:A` | Guarda arma alarme |
| `R:L` / `R:G` | Servidor define o papel |
| `S:...` | Estado filtrado do jogo (tick, mapa visível, itens, vez, saída…) |
| `EV:...` | Evento (alarme, cofre, captura…) |
| `E:...` | Ação recusada (parede, vez errada, sem item…) |
| `FIM:L` / `FIM:G` | Vencedor |

### Organização do código

```text
src/main/java/br/com/policialadrao/
├── client/
│   ├── BoardPanel.java    desenho do mapa, personagens e itens
│   ├── ClientMain.java    inicialização da interface
│   ├── GameClient.java    socket e leitura das mensagens
│   ├── GameWindow.java    telas de conexão e de jogo, controles
│   ├── StartPanel.java    entrada de nome e servidor
│   └── UiStyle.java       cores e componentes visuais
├── common/
│   ├── Direction.java     direções e códigos de movimento
│   ├── GameSnapshot.java  estado filtrado enviado ao cliente
│   ├── MapLayout.java     grade 7×7 e paredes
│   ├── PixelAssets.java   carregamento dos sprites PNG
│   ├── Protocol.java      codificação/decodificação das mensagens
│   └── Role.java          Ladrão e Guarda
└── server/
    ├── GameConfig.java    tempos, alcances e quantidades
    ├── GameEngine.java    regras e estado protegido pelo mutex
    ├── GameServer.java    sockets e threads dos jogadores
    └── ServerMain.java    inicialização do servidor
```

Os sprites ficam em `src/main/resources/assets/`.

---

## Compilar e gerar o JAR

Requer **JDK 17 ou superior** no `PATH`.

```bat
build.bat     :: só compila para build\classes
package.bat   :: compila e gera dist\policia-e-ladrao.jar
```

Para rodar pelo JAR:

```bat
java -jar dist\policia-e-ladrao.jar                          :: cliente
java -cp dist\policia-e-ladrao.jar br.com.policialadrao.server.ServerMain          :: servidor normal
java -cp dist\policia-e-ladrao.jar br.com.policialadrao.server.ServerMain --demo   :: servidor demo
```

## Observações

- O servidor aceita só dois jogadores por partida; feche e abra de novo para outra.
- Se o jogo for em PCs diferentes, libere a porta **5050** no firewall do PC servidor.
- O mapa, os poderes e os tempos foram ajustados em relação ao documento original do projeto (que previa grade 11×11 e movimento simultâneo) para deixar a partida mais dinâmica em turnos.
