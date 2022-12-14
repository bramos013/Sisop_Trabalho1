// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// 	  VM
//    HW = memória, cpu
//    SW = tratamento int e chamada de sistema
// Funcionalidades de carga, execução e dump de memória

import javax.swing.*;
import java.util.*;
import java.util.concurrent.Semaphore;

public class Sistema {

    // -------------------------------------------------------------------------------------------------------
    // --------------------- H A R D W A R E - definicoes de HW ----------------------------------------------

    // -------------------------------------------------------------------------------------------------------
    // --------------------- M E M O R I A -  definicoes de palavra de memoria, memória ----------------------

    public class Memory {
        public int tamMem;
        public Word[] m;                  // m representa a memória fisica:   um array de posicoes de memoria (word)

        public Memory(int size) {
            tamMem = size;
            m = new Word[tamMem];
            for (int i = 0; i < tamMem; i++) {
                m[i] = new Word(Opcode.___, -1, -1, -1);
            }
            ;
        }

        public void dump(Word w) {        // funcoes de DUMP nao existem em hardware - colocadas aqui para facilidade
            System.out.print("[ ");
            System.out.print(w.opc);
            System.out.print(", ");
            System.out.print(w.r1);
            System.out.print(", ");
            System.out.print(w.r2);
            System.out.print(", ");
            System.out.print(w.p);
            System.out.println("  ] ");
        }

        public void dump(int ini, int fim) {
            for (int i = ini; i < fim; i++) {
                System.out.print(i);
                System.out.print(":  ");
                dump(m[i]);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------

    public class Word {    // cada posicao da memoria tem uma instrucao (ou um dado)
        public Opcode opc;    //
        public int r1;        // indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
        public int r2;        // indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
        public int p;        // parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

        public Word(Opcode _opc, int _r1, int _r2, int _p) {  // vide definição da VM - colunas vermelhas da tabela
            opc = _opc;
            r1 = _r1;
            r2 = _r2;
            p = _p;
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // --------------------- C P U  -  definicoes da CPU ----------------------------------------------------- 

    public enum Opcode {
        DATA, ___,                            // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
        JMP, JMPI, JMPIG, JMPIL, JMPIE,     // desvios e parada
        JMPIM, JMPIGM, JMPILM, JMPIEM, STOP,
        JMPIGK, JMPILK, JMPIEK, JMPIGT,
        ADDI, SUBI, ADD, SUB, MULT,         // matematicos
        LDI, LDD, STD, LDX, STX, MOVE,      // movimentacao
        TRAP                                // chamada de sistema
    }

    public enum Interrupts {               // possiveis interrupcoes que esta CPU gera
        noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow, intSTOP, intIO;
    }

    public enum STATE {
        RUNNING,
        READY,
        BLOCKED;
    }


    public class CPU {
        // valores maximo e minimo para inteiros nesta cpu
        private int maxInt;
        private int minInt;
        // característica do processador: contexto da CPU ...
        // composto de program counter,
        private int pc;
        // instruction register
        private Word ir;
        // registradores da CPU
        private int[] reg;
        // durante instrucao, interrupcao pode ser sinalizada
        private Interrupts irpt;
        // estado da CPU
        private Stack<Interrupts> stackInterrupt;
        Semaphore semaphoreCPU;
        // base e limite de acesso na memoria
        private int base;
        // por enquanto toda memoria pode ser acessada pelo processo rodando
        private int limite;
        // ATE AQUI: contexto da CPU - tudo que precisa sobre o estado
        // de um processo para executa-lo
        // nas proximas versoes isto pode modificar

        // mem tem funcoes de dump e o array m de memória 'fisica'
        private Memory mem;
        // CPU acessa MEMORIA, guarda referencia a 'm'. m nao muda. sempre
        // será um array de palavras
        private Word[] m;

        GM.tabelaPaginaProcesso pagesProcess;
        STATE state; // É usado pelo escalonador

        // significa desvio para rotinas de tratamento de  Int - se int ligada, desvia
        private InterruptHandling ih;
        // significa desvio para tratamento de chamadas de sistema - trap
        private SysCallHandling sysCall;
        // se true entao mostra cada instrucao em execucao
        private boolean debug;

        public CPU(
                Memory _mem,
                InterruptHandling _ih,
                SysCallHandling _sysCall,
                boolean _debug
        ) throws InterruptedException {// ref a MEMORIA e interrupt handler passada na criacao da CPU
            // Capacidade de representacao modelada
            maxInt = 32767;
            // Se exceder deve gerar interrupcao de overflow
            minInt = -32767;
            // Usa mem para acessar funcoes auxiliares (dump)
            mem = _mem;
            // Usa o atributo 'm' para acessar a memoria.
            m = mem.m;
            // 10 registradores
            reg = new int[10];
            // Aponta para rotinas de tratamento de int
            ih = _ih;
            // Aponta para rotinas de tratamento de chamadas de sistema
            sysCall = _sysCall;
            // Se true, print da instrucao em execucao
            debug = _debug;
            // Inicializa o program counter
            semaphoreCPU = new Semaphore(2);
            // Trava CPU ao iniciar o SISTEMA
            semaphoreCPU.acquire(2);
        }

        private boolean legal(int e) { // todo acesso a memoria tem que ser verificado
            // ????
            return true;
        }

        private boolean testOverflow(int v) { // toda operacao matematica deve avaliar se ocorre overflow
            if ((v < minInt) || (v > maxInt)) {
                irpt = Interrupts.intOverflow;
                return false;
            }
            ;
            return true;
        }

        public void setContextBase(int _base, int _limite, int _pc) {  // no futuro esta funcao vai ter que ser
            base = _base;                                          // expandida para setar todo contexto de execucao,
            limite = _limite;                                       // agora,  setamos somente os registradores base,
            pc = _pc;                                              // limite e pc (deve ser zero nesta versao)
            irpt = Interrupts.noInterrupt;                         // reset da interrupcao registrada
        }

        public void setContext(int _pc, GM.tabelaPaginaProcesso pagesProcess, STATE state) {
            pc = _pc;
            this.pagesProcess = pagesProcess;
            this.state = state;
        }

        public void setInterrupt(Interrupts interrupt) {

            this.stackInterrupt.push(interrupt);
            if (interrupt == interrupt.intIO) {
                JOptionPane.showMessageDialog(null, "Interrupção de IO recebida");
                vm.cpu.semaphoreCPU.release();

            }
        }

        /**
         * Libera o semaforo da CPU.
         * {@code semaphoreCPU.release()}
         */
        public void setRunning() {
            semaphoreCPU.release();
        }

        public void run() { // execucao da CPU supoe que o contexto da CPU, vide acima, esta devidamente setado
            while (true) {  // ciclo de instrucoes. acaba cfe instrucao, veja cada caso.
                // --------------------------------------------------------------------------------------------------
                // FETCH
                if (legal(pc)) { // pc valido
                    ir = m[pc];  // busca posição da mémoria apontada por pc, guarda em ir
                    if (debug) {
                        System.out.print("      pc: " + pc + "       exec: ");
                        mem.dump(ir);
                    }
                    // --------------------------------------------------------------------------------------------------
                    // EXECUTA INSTRUCAO NO ir
                    switch (ir.opc) { // conforme o opcode (código de operação) executa

                        // Instrucoes de Busca e Armazenamento em Memoria
                        case LDI: // Rd ← k
                            reg[ir.r1] = ir.p;
                            pc++;
                            break;

                        case LDD: // Rd <- [A]
                            if (legal(ir.p)) {
                                reg[ir.r1] = m[ir.p].p;
                                pc++;
                            }
                            break;

                        case LDX: // RD <- [RS] // NOVA
                            if (legal(reg[ir.r2])) {
                                reg[ir.r1] = m[reg[ir.r2]].p;
                                pc++;
                            }
                            break;

                        case STD: // [A] ← Rs
                            if (legal(ir.p)) {
                                m[ir.p].opc = Opcode.DATA;
                                m[ir.p].p = reg[ir.r1];
                                pc++;
                            }
                            ;
                            break;

                        case STX: // [Rd] ←Rs
                            if (legal(reg[ir.r1])) {
                                m[reg[ir.r1]].opc = Opcode.DATA;
                                m[reg[ir.r1]].p = reg[ir.r2];
                                pc++;
                            }
                            ;
                            break;

                        case MOVE: // RD <- RS
                            reg[ir.r1] = reg[ir.r2];
                            pc++;
                            break;

                        // Instrucoes Aritmeticas
                        case ADD: // Rd ← Rd + Rs
                            reg[ir.r1] = reg[ir.r1] + reg[ir.r2];
                            testOverflow(reg[ir.r1]);
                            pc++;
                            break;

                        case ADDI: // Rd ← Rd + k
                            reg[ir.r1] = reg[ir.r1] + ir.p;
                            testOverflow(reg[ir.r1]);
                            pc++;
                            break;

                        case SUB: // Rd ← Rd - Rs
                            reg[ir.r1] = reg[ir.r1] - reg[ir.r2];
                            testOverflow(reg[ir.r1]);
                            pc++;
                            break;

                        case SUBI: // RD <- RD - k // NOVA
                            reg[ir.r1] = reg[ir.r1] - ir.p;
                            testOverflow(reg[ir.r1]);
                            pc++;
                            break;

                        case MULT: // Rd <- Rd * Rs
                            reg[ir.r1] = reg[ir.r1] * reg[ir.r2];
                            testOverflow(reg[ir.r1]);
                            pc++;
                            break;

                        // Instrucoes JUMP
                        case JMP: // PC <- k
                            pc = ir.p;
                            break;

                        case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
                            if (reg[ir.r2] > 0) {
                                pc = reg[ir.r1];
                            } else {
                                pc++;
                            }
                            break;

                        case JMPIGK: // If RC > 0 then PC <- k else PC++
                            if (reg[ir.r2] > 0) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;

                        case JMPILK: // If RC < 0 then PC <- k else PC++
                            if (reg[ir.r2] < 0) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;

                        case JMPIEK: // If RC = 0 then PC <- k else PC++
                            if (reg[ir.r2] == 0) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;


                        case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
                            if (reg[ir.r2] < 0) {
                                pc = reg[ir.r1];
                            } else {
                                pc++;
                            }
                            break;

                        case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
                            if (reg[ir.r2] == 0) {
                                pc = reg[ir.r1];
                            } else {
                                pc++;
                            }
                            break;

                        case JMPIM: // PC <- [A]
                            pc = m[ir.p].p;
                            break;

                        case JMPIGM: // If RC > 0 then PC <- [A] else PC++
                            if (reg[ir.r2] > 0) {
                                pc = m[ir.p].p;
                            } else {
                                pc++;
                            }
                            break;

                        case JMPILM: // If RC < 0 then PC <- k else PC++
                            if (reg[ir.r2] < 0) {
                                pc = m[ir.p].p;
                            } else {
                                pc++;
                            }
                            break;

                        case JMPIEM: // If RC = 0 then PC <- k else PC++
                            if (reg[ir.r2] == 0) {
                                pc = m[ir.p].p;
                            } else {
                                pc++;
                            }
                            break;

                        case JMPIGT: // If RS>RC then PC <- k else PC++
                            if (reg[ir.r1] > reg[ir.r2]) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;

                        // outras
                        case STOP: // por enquanto, para execucao
                            irpt = Interrupts.intSTOP;
                            break;

                        case DATA:
                            irpt = Interrupts.intInstrucaoInvalida;
                            break;

                        // Chamada de sistema
                        case TRAP:
                            sysCall.handle(); // <<<<< aqui desvia para rotina de chamada de sistema, no momento so temos IO
                            pc++;
                            break;

                        // Inexistente
                        default:
                            irpt = Interrupts.intInstrucaoInvalida;
                            break;
                    }
                }
                // --------------------------------------------------------------------------------------------------
                // VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
                if (!(irpt == Interrupts.noInterrupt)) {   // existe interrupção
                    ih.handle(irpt, pc);                       // desvia para rotina de tratamento
                    break; // break sai do loop da cpu
                }
            }  // FIM DO CICLO DE UMA INSTRUÇÃO
        }
    }
    // ------------------ C P U - fim ------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

    // ------------------- V M  - constituida de CPU e MEMORIA -----------------------------------------------
    // -------------------------- atributos e construcao da VM -----------------------------------------------
    public class VM {
        public int tamMem, tamFrame, num_partition;
        public Word[] m;
        public GM gm;
        public Memory mem;
        public CPU cpu;

        public VM(InterruptHandling ih, SysCallHandling sysCall) throws InterruptedException {
            // vm deve ser configurada com endereço de tratamento de interrupcoes e de chamadas de sistema
            // cria memória
            tamMem = 1024;
            mem = new Memory(tamMem);
            m = mem.m;
            // cria cpu
            cpu = new CPU(mem, ih, sysCall, true); // true liga debug
        }
    }


    // ------------------- V M  - fim ------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

    // --------------------H A R D W A R E - fim -------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

    // -------------------------------------------------------------------------------------------------------

    // -------------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------
    // ------------------- S O F T W A R E - inicio ----------------------------------------------------------

    // ------------------- G E R E N T E  M E M O R I A - inicio

    // ------------------------------------------- funcoes de um monitor
    public class Monitor {
        GP gp;

        public Monitor() throws InterruptedException {
            gp = new GP();
        }

        public void dump(Word w) {
            System.out.print("[ ");
            System.out.print(w.opc);
            System.out.print(", ");
            System.out.print(w.r1);
            System.out.print(", ");
            System.out.print(w.r2);
            System.out.print(", ");
            System.out.print(w.p);
            System.out.println("  ] ");
        }

        public void dump(Word[] m, int ini, int fim) {
            for (int i = ini; i < fim; i++) {
                System.out.print(i);
                System.out.print(":  ");
                dump(m[i]);
            }
        }

        /**
         * Dump frames da memoria
         */
        public void dumpAllFrames() {

            for (int i = 0; i < vm.num_partition; i++) {
                System.out.println("----------------------------");
                System.out.println("FRAME " + i + ":");

                vm.gm.dumpFrame(i);

                System.out.println("----------------------------");
            }

        }

        public void dumpId(int id) {
            for (GP.PCB p : monitor.gp.listProcess) {
                if (p.getId() == id) {
                    System.out.println("----------------------------------------");
                    System.out.println("PCB Info");
                    System.out.println("Process id: " + p.id);
                    System.out.println("Atual pc: " + p.pcContext);
                    System.out.println("Lista de frames ocupados pelo processo: " + p.tPaginaProcesso.toString());
                    System.out.println("Memoria dos frames");
                    for (int frame : p.tPaginaProcesso.tabela) {
                        vm.gm.dumpFrame(frame);
                    }
                }

            }

        }

        public void ps() {
            for (GP.PCB p : monitor.gp.listProcess) {
                System.out.println("----------------------------------------");
                System.out.print("PCB Info");
                System.out.print("		| Process id: " + p.id);
                System.out.print("		| Atual pc: " + p.pcContext);
                System.out.print("		| Lista de frames ocupados pelo processo: " + p.tPaginaProcesso.toString());
                System.out.println("	| State: " + p.getStateString());

            }

        }

        // significa ler "p" de
        // memoria secundaria e
        // colocar na principal "m"
        public void carga(Word[] p, Word[] m, GM.tabelaPaginaProcesso paginasDoProcesso) {
            for (int i = 0; i < p.length; i++) {
                int t = vm.gm.translate(i, paginasDoProcesso);
                m[t].opc = p[i].opc;
                m[t].r1 = p[i].r1;
                m[t].r2 = p[i].r2;
                m[t].p = p[i].p;
            }
        }

        // Escalonador
        public void executa(int id) {
            // Precisa fazer isso para que a cpu não fique em loop infinito
            //vm.cpu.resetInterrupt();

            GP.PCB CurrentProcess = null;
            // Procura o processo na lista de processos
            for (int i = 0; i < monitor.gp.listProcess.size(); i++) {
                if (monitor.gp.listProcess.get(i).id == id)
                    CurrentProcess = monitor.gp.listProcess.get(i);

            }
            // Se o processo não existir, retorna PROGRAMA NAO ENCONTRADO
            if (CurrentProcess == null) {
                System.out.println("PROGRAMA NAO ENCONTRADO");

            } else {
                gp.CurrentProcessGP = CurrentProcess;
                // Muda o estado do processo para RUNNING
                gp.CurrentProcessGP.setState(STATE.RUNNING);
                // Muda o contexto da CPU para o processo
                vm.cpu.setContext(
                    CurrentProcess.getPc(),
                    CurrentProcess.getTPaginaProcesso(),
                    STATE.RUNNING
                );
                // Executa o processo
                vm.cpu.setRunning();
            }
        }

    }

    // -------------------------------------------

    /**
     * Gerenciador de processos
     */
    public class GP {
        private Escalonador escalonador;

        public GP() throws InterruptedException {
            escalonador = new Escalonador();
        }

        /**
         * ListProcess = lista com todos os processos criados que estão em memoria
         */
        ArrayList<PCB> listProcess = new ArrayList<PCB>();
        PCB CurrentProcessGP;

        int uniqueId = 0;

        public int getUniqueId() {
            int idReturn = this.uniqueId;
            this.uniqueId++;
            return idReturn;
        }

        /**
         * Escalonador implementa FIFS(First-In First-Served)
         * Nao necessita parametros, pois ira acessar a variavel do processo corrente em
         * execucao
         */

        /**
         * Escalonador libera o semaforo para novo escalonamento
         */
        public void Escalonador() {
            monitor.gp.escalonador.semaphoreScheduler.release();
        }

        /**
         * Implementa rotinas de escalonamento com controle de concorrencia
         */
        public class Escalonador extends Thread {
            public Semaphore semaphoreScheduler;

            public Escalonador() throws InterruptedException {
                semaphoreScheduler = new Semaphore(1);
                semaphoreScheduler.acquire();// trava o semaforo inicial
                start();
            }

            public void run() {

                while (true) {
                    // aguarda bloqueado
                    try {

                        semaphoreScheduler.acquire();
                        // Salva o contexto
                        if (CurrentProcessGP != null) {// verifica se o processo atual não é nulo
                            CurrentProcessGP.setPc(vm.cpu.pc);

                            if (CurrentProcessGP.state == STATE.RUNNING)
                                CurrentProcessGP.setState(STATE.READY);// (re)coloca o estado atual como pronto
                        }
                        ArrayList<Integer> ReadyProcess = new ArrayList<>();
                        listProcess.stream()
                                .filter(e -> e.getState() == STATE.READY)
                                .forEach(a -> ReadyProcess.add(a.getId())); // Filtra processos em estadosgetId() pronto

                        if (ReadyProcess.size() != 0) {

                            System.out.println("processos prontos ID: ");
                            ReadyProcess.stream().forEach(e -> System.out.print("|" + e + "|"));
                            System.out.println();

                            monitor.executa(ReadyProcess.get(0));// executa o primeiro processo filtrado do estado
                            // pronto

                        } else {
                            System.out.println("Sem processos prontos para execucao...");

                        }

                    } catch (InterruptedException e1) {
                        // TODO Auto-generated catch block
                        System.out.println(e1);
                        e1.printStackTrace();
                    }

                }
            }
        }

        /**
         * Metodo para criação de processo
         *
         * @param programa
         * @return true: alocação com sucesso
         * @return false: alocação falhou
         */
        public int createProcesso(Word[] programa) {
            int programSize = programa.length;
            if (programSize > vm.tamMem)
                return -1; // verifica o tamanho do programa

            GM.tabelaPaginaProcesso newPages = vm.gm.new tabelaPaginaProcesso();
            // faz alocação das páginas
            boolean sucessAlocation = vm.gm.alloc(
                    programSize,
                    newPages
            );
            if (sucessAlocation) {
                // id igual o ultimo tamanho do array de processos
                int id = getUniqueId();
                monitor.carga(programa, vm.m, newPages);
                PCB P = new PCB(id, 0, newPages);
                listProcess.add(P);
                return id;
            }

            return -1;

        }

        public void deallocate(int id) {
            for (int i = 0; i < listProcess.size(); i++) {
                PCB Process = listProcess.get(i);
                if (Process.id == id) {
                    // desaloca os frames ocupados
                    for (Integer framePos : Process.tPaginaProcesso.tabela) {
                        vm.gm.partition_free_list[framePos] = true;
                    }
                    listProcess.remove(Process);

                }
            }
        }

        ////////////////////////////////////
        // ---------------PCB----------------/

        public class PCB {
            private int id;
            private int pcContext;
            private STATE state;
            private GM.tabelaPaginaProcesso tPaginaProcesso;

            public PCB(int id, int pc, GM.tabelaPaginaProcesso tPaginaProcesso) {
                this.id = id;
                this.pcContext = pc;
                this.tPaginaProcesso = tPaginaProcesso;
                this.state = STATE.READY;
            }

            public int getId() {
                return this.id;
            }

            public int getPc() {
                return this.pcContext;
            }

            public void setPc(int newPc) {
                this.pcContext = newPc;
            }

            public void setState(STATE state) {
                this.state = state;
            }

            public STATE getState() {
                return this.state;
            }

            public String getStateString() {
                switch (this.state) {
                    case READY:
                        return "Ready";
                    case RUNNING:
                        return "Running";
                    case BLOCKED:
                        return "Blocked";
                    default:
                        return "STATE FAILED!";

                }
            }

            public GM.tabelaPaginaProcesso getTPaginaProcesso() {
                return this.tPaginaProcesso;

            }

        }
    }

    public class GM {
        int memory_size;
        int partition_size;
        boolean partition_free_list[];    //lista de partições livres de alocação

        /**
         * habilita a alocação de paginas durante execução PODE GERAR SOBRESCRITA! \n
         * Ex: caso o programa tente acessar uma posição de memória ou pagina que não é
         * sua, é verificado se está disponivel.
         * Se disponivel, ira alocar uma nova pagina para o processo poder prosseguir
         *
         * @return "Um aviso de nova alocação é informado"
         */
        public boolean dynamicOverridePages = true;

        // PRONTO
        public GM(int memory_size, int partition_size) { // construtor
            this.memory_size = memory_size;    //tamanho da memória
            this.partition_size = partition_size; //tamanho da partição

            //quantidade de partições é = ao tamanho da memória dividido pelo tamanho da partição
            int num_partition = memory_size / partition_size;
            partition_free_list = new boolean[num_partition]; // vetor de particoes livres

            for (int i = 0; i < num_partition; i++) { // inicializa todas as particoes como livres
                partition_free_list[i] = true;
            }
        }

        // DOING
        public boolean alloc(int program_size, tabelaPaginaProcesso paginas) { // aloca uma particao de memoria
            int num_partition_free = 0;
            // numero de particoes necessarias para alocar o processo
            int required_partition = program_size / partition_size;

            int offset = program_size % this.partition_size;
            if (offset > 0) // se houver divisao quebrada, necessita mais uma partição
                required_partition++;

            for (boolean partition_free : partition_free_list) { // calculando frames livres
                if (partition_free == true)
                    num_partition_free++;
            }

            if (required_partition <= num_partition_free) { // se houver particoes livres suficientes
                while (required_partition >= 0) { // enquanto houver particoes necessarias
                    for (int i = 0; i < partition_free_list.length; i++) { // percorre a lista de particoes livres
                        if (partition_free_list[i] == true) { // se a particao estiver livre
                            partition_free_list[i] = false; // alocando particao
                            paginas.tabela.add(i);
                            required_partition--; // decrementa particoes necessarias
                            break; // sai do for
                        }
                    }
                }
                return true; // alocou
            }
            return false; // nao alocou
        }

        public void dumpFrame(int frame) {
            System.out.println("FrameLivre: " + vm.gm.partition_free_list[frame]);

            int pInicio = frame * vm.tamFrame;
            int pFim = pInicio + partition_size;
            monitor.dump(vm.m, pInicio, pFim);
        }

        /**
         * Tradutor do endereco lógico
         * input: object tabela / int endereco logico
         * * @return int posicao real na memoria
         */
        public int translate(int posicaoSolicitada, tabelaPaginaProcesso t) {
            int totalFrames = t.tabela.size();
            int p = posicaoSolicitada / partition_size; // p = contagem de posicao no array da tabela
            int offset = posicaoSolicitada % partition_size; // offset desclocamente dentro do frame

            if (p >= totalFrames && this.dynamicOverridePages) { // verifica se durante a exexcução foi requisitado
                // algum endereco fora do escopo de paginas
                boolean sucessNewAllocaded = alloc(1, t); // aloca nova pagina para posição
                if (sucessNewAllocaded) {
                    System.out.println("warning: new page is allocated");
                } else {
                    vm.cpu.setInterrupt(Interrupts.intEnderecoInvalido);
                    // se nao conseguiu alocar, retorna problema de
                    // acesso a memoria
                }
            }

            int frameInMemory = t.tabela.get(p); // obtem no indice de paginas o frame real da memoria
            int positionInMemory = partition_size * frameInMemory + offset;

            return positionInMemory;
        }


        /**
         * Tabela das paginas do processos
         */
        public class tabelaPaginaProcesso { // classe para modulalizar como objeto as tabelas. Cada processo possui sua
            // tabela
            ArrayList<Integer> tabela;

            public tabelaPaginaProcesso() {
                tabela = new ArrayList<>();
            }

            @Override
            public String toString() {
                String output = "";
                for (Integer i : tabela) {
                    output += " | " + i + " | ";
                }

                return output;
            }

        }

    }

    // ------------------- G E R E N T E  M E M O R I A - fim

    // ------------------- I N T E R R U P C O E S  - rotinas de tratamento ----------------------------------
    public class InterruptHandling {
        // apenas avisa - todas interrupcoes neste momento finalizam o programa
        public void handle(Interrupts irpt, int pc) {
            System.out.println("                                               Interrupcao " + irpt + "   pc: " + pc);
        }
    }

    // ------------------- C H A M A D A S  D E  S I S T E M A  - rotinas de tratamento ----------------------
    public class SysCallHandling {
        private VM vm;

        public void setVM(VM _vm) {
            vm = _vm;
        }

        public void handle() {   // apenas avisa - todas interrupcoes neste momento finalizam o programa
            System.out.println("Chamada de Sistema com op  /  par:  " + vm.cpu.reg[8] + " / " + vm.cpu.reg[9]);
        }
    }

    // ------------------ U T I L I T A R I O S   D O   S I S T E M A -----------------------------------------
    // ------------------ load é invocado a partir de requisição do usuário

    private void loadProgram(Word[] p, Word[] m) {
        for (int i = 0; i < p.length; i++) {
            m[i].opc = p[i].opc;
            m[i].r1 = p[i].r1;
            m[i].r2 = p[i].r2;
            m[i].p = p[i].p;
        }
    }

    private void loadProgram(Word[] p) {
        loadProgram(p, vm.m);
    }

    private void loadAndExec(Word[] p) {
        loadProgram(p);    // carga do programa na memoria
        System.out.println("---------------------------------- programa carregado na memoria");
        vm.mem.dump(0, p.length);            // dump da memoria nestas posicoes
        vm.cpu.setContextBase(0, vm.tamMem - 1, 0);      // seta estado da cpu ]
        System.out.println("---------------------------------- inicia execucao ");
        vm.cpu.run();                                // cpu roda programa ate parar
        System.out.println("---------------------------------- memoria após execucao ");
        vm.mem.dump(0, p.length);            // dump da memoria com resultado
    }

    // -------------------------------------------------------------------------------------------------------
    // -------------------  S I S T E M A --------------------------------------------------------------------

    public VM vm;
    public Monitor monitor;
    public InterruptHandling ih;
    public SysCallHandling sysCall;
    public static Programas progs;

    public Sistema() throws InterruptedException {   // a VM com tratamento de interrupções
        ih = new InterruptHandling();
        sysCall = new SysCallHandling();
        vm = new VM(ih, sysCall);
        sysCall.setVM(vm);
        progs = new Programas();
    }

    // -------------------  S I S T E M A - fim --------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

    // -------------------------------------------------------------------------------------------------------
    // ------------------- instancia e testa sistema
    public static void main(String args[]) throws InterruptedException {
        Sistema s = new Sistema();
        //s.loadAndExec(progs.fibonacci10);
        //s.loadAndExec(progs.progMinimo);
        s.loadAndExec(progs.fatorial);
        //s.loadAndExec(progs.fatorialTRAP); // saida
        //s.loadAndExec(progs.fibonacciTRAP); // entrada
        //s.loadAndExec(progs.PC); // bubble sort

    }


    // -------------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------
    // --------------- P R O G R A M A S  - não fazem parte do sistema
    // esta classe representa programas armazenados (como se estivessem em disco)
    // que podem ser carregados para a memória (load faz isto)

    public class Programas {
        public Word[] fatorial = new Word[]{
                // este fatorial so aceita valores positivos.   nao pode ser zero
                // linha   coment
                new Word(Opcode.LDI, 0, -1, 4),      // 0   	r0 é valor a calcular fatorial
                new Word(Opcode.LDI, 1, -1, 1),      // 1   	r1 é 1 para multiplicar (por r0)
                new Word(Opcode.LDI, 6, -1, 1),      // 2   	r6 é 1 para ser o decremento
                new Word(Opcode.LDI, 7, -1, 8),      // 3   	r7 tem posicao de stop do programa = 8
                new Word(Opcode.JMPIE, 7, 0, 0),     // 4   	se r0=0 pula para r7(=8)
                new Word(Opcode.MULT, 1, 0, -1),     // 5   	r1 = r1 * r0
                new Word(Opcode.SUB, 0, 6, -1),      // 6   	decrementa r0 1
                new Word(Opcode.JMP, -1, -1, 4),     // 7   	vai p posicao 4
                new Word(Opcode.STD, 1, -1, 10),     // 8   	coloca valor de r1 na posição 10
                new Word(Opcode.STOP, -1, -1, -1),   // 9   	stop

                // 10 ao final o valor do fatorial estará na posição 10 da memória
                new Word(Opcode.DATA, -1, -1, -1)};

        public Word[] progMinimo = new Word[]{
                new Word(Opcode.LDI, 0, -1, 999),
                new Word(Opcode.STD, 0, -1, 10),
                new Word(Opcode.STD, 0, -1, 11),
                new Word(Opcode.STD, 0, -1, 12),
                new Word(Opcode.STD, 0, -1, 13),
                new Word(Opcode.STD, 0, -1, 14),
                new Word(Opcode.STOP, -1, -1, -1)};

        public Word[] fibonacci10 = new Word[]{ // mesmo que prog exemplo, so que usa r0 no lugar de r8
                new Word(Opcode.LDI, 1, -1, 0),
                new Word(Opcode.STD, 1, -1, 20),
                new Word(Opcode.LDI, 2, -1, 1),
                new Word(Opcode.STD, 2, -1, 21),
                new Word(Opcode.LDI, 0, -1, 22),
                new Word(Opcode.LDI, 6, -1, 6),
                new Word(Opcode.LDI, 7, -1, 31),
                new Word(Opcode.LDI, 3, -1, 0),
                new Word(Opcode.ADD, 3, 1, -1),
                new Word(Opcode.LDI, 1, -1, 0),
                new Word(Opcode.ADD, 1, 2, -1),
                new Word(Opcode.ADD, 2, 3, -1),
                new Word(Opcode.STX, 0, 2, -1),
                new Word(Opcode.ADDI, 0, -1, 1),
                new Word(Opcode.SUB, 7, 0, -1),
                new Word(Opcode.JMPIG, 6, 7, -1),
                new Word(Opcode.STOP, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),   // POS 20
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)}; // ate aqui - serie de fibonacci ficara armazenada

        public Word[] fatorialTRAP = new Word[]{
                new Word(Opcode.LDI, 0, -1, 7),// numero para colocar na memoria
                new Word(Opcode.STD, 0, -1, 50),
                new Word(Opcode.LDD, 0, -1, 50),
                new Word(Opcode.LDI, 1, -1, -1),
                new Word(Opcode.LDI, 2, -1, 13),// SALVAR POS STOP
                new Word(Opcode.JMPIL, 2, 0, -1),// caso negativo pula pro STD
                new Word(Opcode.LDI, 1, -1, 1),
                new Word(Opcode.LDI, 6, -1, 1),
                new Word(Opcode.LDI, 7, -1, 13),
                new Word(Opcode.JMPIE, 7, 0, 0), //POS 9 pula pra STD (Stop-1)
                new Word(Opcode.MULT, 1, 0, -1),
                new Word(Opcode.SUB, 0, 6, -1),
                new Word(Opcode.JMP, -1, -1, 9),// pula para o JMPIE
                new Word(Opcode.STD, 1, -1, 18),
                new Word(Opcode.LDI, 8, -1, 2),// escrita
                new Word(Opcode.LDI, 9, -1, 18),//endereco com valor a escrever
                new Word(Opcode.TRAP, -1, -1, -1),
                new Word(Opcode.STOP, -1, -1, -1), // POS 17
                new Word(Opcode.DATA, -1, -1, -1)};//POS 18

        public Word[] fibonacciTRAP = new Word[]{ // mesmo que prog exemplo, so que usa r0 no lugar de r8
                new Word(Opcode.LDI, 8, -1, 1),// leitura
                new Word(Opcode.LDI, 9, -1, 100),//endereco a guardar
                new Word(Opcode.TRAP, -1, -1, -1),
                new Word(Opcode.LDD, 7, -1, 100),// numero do tamanho do fib
                new Word(Opcode.LDI, 3, -1, 0),
                new Word(Opcode.ADD, 3, 7, -1),
                new Word(Opcode.LDI, 4, -1, 36),//posicao para qual ira pular (stop) *
                new Word(Opcode.LDI, 1, -1, -1),// caso negativo
                new Word(Opcode.STD, 1, -1, 41),
                new Word(Opcode.JMPIL, 4, 7, -1),//pula pra stop caso negativo *
                new Word(Opcode.JMPIE, 4, 7, -1),//pula pra stop caso 0
                new Word(Opcode.ADDI, 7, -1, 41),// fibonacci + posição do stop
                new Word(Opcode.LDI, 1, -1, 0),
                // 25 posicao de memoria onde inicia a serie de fibonacci gerada
                new Word(Opcode.STD, 1, -1, 41),
                new Word(Opcode.SUBI, 3, -1, 1),// se 1 pula pro stop
                new Word(Opcode.JMPIE, 4, 3, -1),
                new Word(Opcode.ADDI, 3, -1, 1),
                new Word(Opcode.LDI, 2, -1, 1),
                new Word(Opcode.STD, 2, -1, 42),
                new Word(Opcode.SUBI, 3, -1, 2),// se 2 pula pro stop
                new Word(Opcode.JMPIE, 4, 3, -1),
                new Word(Opcode.LDI, 0, -1, 43),
                new Word(Opcode.LDI, 6, -1, 25),// salva posição de retorno do loop
                new Word(Opcode.LDI, 5, -1, 0),//salva tamanho
                new Word(Opcode.ADD, 5, 7, -1),
                new Word(Opcode.LDI, 7, -1, 0),//zera (inicio do loop)
                new Word(Opcode.ADD, 7, 5, -1),//recarrega tamanho
                new Word(Opcode.LDI, 3, -1, 0),
                new Word(Opcode.ADD, 3, 1, -1),
                new Word(Opcode.LDI, 1, -1, 0),
                new Word(Opcode.ADD, 1, 2, -1),
                new Word(Opcode.ADD, 2, 3, -1),
                new Word(Opcode.STX, 0, 2, -1),
                new Word(Opcode.ADDI, 0, -1, 1),
                new Word(Opcode.SUB, 7, 0, -1),
                new Word(Opcode.JMPIG, 6, 7, -1),//volta para o inicio do loop
                new Word(Opcode.STOP, -1, -1, -1),   // POS 36
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),   // POS 41
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
        };

        public Word[] PB = new Word[]{
                //dado um inteiro em alguma posição de memória,
                // se for negativo armazena -1 na saída; se for positivo responde o fatorial do número na saída
                new Word(Opcode.LDI, 0, -1, 7),// numero para colocar na memoria
                new Word(Opcode.STD, 0, -1, 50),
                new Word(Opcode.LDD, 0, -1, 50),
                new Word(Opcode.LDI, 1, -1, -1),
                new Word(Opcode.LDI, 2, -1, 13),// SALVAR POS STOP
                new Word(Opcode.JMPIL, 2, 0, -1),// caso negativo pula pro STD
                new Word(Opcode.LDI, 1, -1, 1),
                new Word(Opcode.LDI, 6, -1, 1),
                new Word(Opcode.LDI, 7, -1, 13),
                new Word(Opcode.JMPIE, 7, 0, 0), //POS 9 pula pra STD (Stop-1)
                new Word(Opcode.MULT, 1, 0, -1),
                new Word(Opcode.SUB, 0, 6, -1),
                new Word(Opcode.JMP, -1, -1, 9),// pula para o JMPIE
                new Word(Opcode.STD, 1, -1, 15),
                new Word(Opcode.STOP, -1, -1, -1), // POS 14
                new Word(Opcode.DATA, -1, -1, -1)}; //POS 15

        public Word[] PC = new Word[]{
                //Para um N definido (10 por exemplo)
                //o programa ordena um vetor de N números em alguma posição de memória;
                //ordena usando bubble sort
                //loop ate que não swap nada
                //passando pelos N valores
                //faz swap de vizinhos se da esquerda maior que da direita
                new Word(Opcode.LDI, 7, -1, 5),// TAMANHO DO BUBBLE SORT (N)
                new Word(Opcode.LDI, 6, -1, 5),//aux N
                new Word(Opcode.LDI, 5, -1, 46),//LOCAL DA MEMORIA
                new Word(Opcode.LDI, 4, -1, 47),//aux local memoria
                new Word(Opcode.LDI, 0, -1, 4),//colocando valores na memoria
                new Word(Opcode.STD, 0, -1, 46),
                new Word(Opcode.LDI, 0, -1, 3),
                new Word(Opcode.STD, 0, -1, 47),
                new Word(Opcode.LDI, 0, -1, 5),
                new Word(Opcode.STD, 0, -1, 48),
                new Word(Opcode.LDI, 0, -1, 1),
                new Word(Opcode.STD, 0, -1, 49),
                new Word(Opcode.LDI, 0, -1, 2),
                new Word(Opcode.STD, 0, -1, 50),//colocando valores na memoria até aqui - POS 13
                new Word(Opcode.LDI, 3, -1, 25),// Posicao para pulo CHAVE 1
                new Word(Opcode.STD, 3, -1, 99),
                new Word(Opcode.LDI, 3, -1, 22),// Posicao para pulo CHAVE 2
                new Word(Opcode.STD, 3, -1, 98),
                new Word(Opcode.LDI, 3, -1, 38),// Posicao para pulo CHAVE 3
                new Word(Opcode.STD, 3, -1, 97),
                new Word(Opcode.LDI, 3, -1, 25),// Posicao para pulo CHAVE 4 (não usada)
                new Word(Opcode.STD, 3, -1, 96),
                new Word(Opcode.LDI, 6, -1, 0),//r6 = r7 - 1 POS 22
                new Word(Opcode.ADD, 6, 7, -1),
                new Word(Opcode.SUBI, 6, -1, 1),//ate aqui
                //CHAVE 3 para pular quando r7 for 1 e r6 0 para interomper o loop de vez do programa
                new Word(Opcode.JMPIEM, -1, 6, 97),
                new Word(Opcode.LDX, 0, 5, -1),//r0 e r1 pegando valores das posições da memoria POS 26
                new Word(Opcode.LDX, 1, 4, -1),
                new Word(Opcode.LDI, 2, -1, 0),
                new Word(Opcode.ADD, 2, 0, -1),
                new Word(Opcode.SUB, 2, 1, -1),
                new Word(Opcode.ADDI, 4, -1, 1),
                new Word(Opcode.SUBI, 6, -1, 1),
                new Word(Opcode.JMPILM, -1, 2, 99),//LOOP chave 1 caso neg procura prox
                new Word(Opcode.STX, 5, 1, -1),
                new Word(Opcode.SUBI, 4, -1, 1),
                new Word(Opcode.STX, 4, 0, -1),
                new Word(Opcode.ADDI, 4, -1, 1),
                new Word(Opcode.JMPIGM, -1, 6, 99),//LOOP chave 1 POS 38
                new Word(Opcode.ADDI, 5, -1, 1),
                new Word(Opcode.SUBI, 7, -1, 1),
                new Word(Opcode.LDI, 4, -1, 0),//r4 = r5 + 1 POS 41
                new Word(Opcode.ADD, 4, 5, -1),
                new Word(Opcode.ADDI, 4, -1, 1),//ate aqui
                new Word(Opcode.JMPIGM, -1, 7, 98),//LOOP chave 2
                new Word(Opcode.STOP, -1, -1, -1), // POS 45
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)};
    }
}

