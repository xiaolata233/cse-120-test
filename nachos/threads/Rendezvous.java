package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
	
	/**
	 * Inner control class
	 */
    public class Control
    {
        Lock lock;
        Condition condition;
        Integer value;
        Integer exchanging;
        public Control()
        {
            this.lock = new Lock();
            condition = new Condition(lock);
            exchanging = 0;
            value = null;
        }
    }
	
    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous () {
        controls = new HashMap<>();
        interruptLock = new Lock();
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {
        //boolean inStatus = Machine.interrupt().disable();
    	interruptLock.acquire();
        Integer new_value = null;
        if(!controls.containsKey(tag)) 
        	controls.put(tag, new Control());
        Control control = controls.get(tag);
        //Machine.interrupt().restore(inStatus);
        interruptLock.release();

        control.lock.acquire();
        // if there are other threads exchanging on the tag, wait
        while(control.exchanging == 1) {
        	//System.out.println("Thread " + KThread.currentThread().getName() + " waiting ");
            control.condition.sleep();
        }

        // if this is the first arrived thread
        if(control.value == null){
            control.value = value;
            control.condition.sleep();
            new_value = control.value;
            control.exchanging = 0;
            control.value = null;
            control.condition.wakeAll();
        }else{
            control.exchanging = 1;
            new_value = control.value;
            control.value = value;
            control.condition.wake();
        }
        control.lock.release();
        return new_value;
    }
    public static void rendezTest1() {
	final Rendezvous r = new Rendezvous();

	KThread t1 = new KThread( new Runnable () {
		public void run() {
		    int tag = 0;
		    int send = -1;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t1.setName("t1");
	KThread t2 = new KThread( new Runnable () {
		public void run() {
		    int tag = 0;
		    int send = 1;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t2.setName("t2");

	t1.fork(); t2.fork();
	// assumes join is implemented correctly
	t1.join(); t2.join();
    }
    public static void rendezTest2() {
	final Rendezvous r = new Rendezvous();

	KThread t1 = new KThread( new Runnable () {
		public void run() {
		    int tag = 0;
		    int send = -1;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t1.setName("t1");
	KThread t2 = new KThread( new Runnable () {
		public void run() {
		    int tag = 0;
		    int send = 1;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t2.setName("t2");
	
	KThread t3 = new KThread( new Runnable () {
		public void run() {
		    int tag = 1;
		    int send = 5;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == -5, "Was expecting " + -5 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t3.setName("t3");

	KThread t4 = new KThread( new Runnable () {
		public void run() {
		    int tag = 1;
		    int send = -5;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == 5, "Was expecting " + 5 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t4.setName("t4");
	
	t1.fork(); t2.fork(); t3.fork(); t4.fork();
	// assumes join is implemented correctly
	t1.join(); t2.join(); t3.join(); t4.join();
    }
    
    public static void rendezTest3() {
	final Rendezvous r = new Rendezvous();

	KThread t1 = new KThread( new Runnable () {
		public void run() {
		    int tag = 0;
		    int send = -1;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t1.setName("t1");
	KThread t2 = new KThread( new Runnable () {
		public void run() {
		    int tag = 1;
		    int send = 1;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
	t2.setName("t2");

	t1.fork(); t2.fork();
	// assumes join is implemented correctly
	t1.join(); t2.join();
    }
    

    // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()

    public static void selfTest() {
    	// place calls to your Rendezvous tests that you implement here
    	rendezTest1();
    	rendezTest2();
    	// commenting this test out, this test is suppose to hang the program
    	// since there is only one thread for each tag, the thread waits forever
    	// for the 2nd thread to come in
    	//rendezTest3();
    }
    
    private HashMap<Integer, Control> controls;
    private static Lock interruptLock;

}
