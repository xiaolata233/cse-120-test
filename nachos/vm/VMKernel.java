package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.userprog.UserKernel;
import nachos.vm.*;

import java.util.HashMap;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	public static void swapOut(int ppn){

	}

	public static void swapIn(int ppn){

	}

	public static boolean evictPage(){
	    // race condition
	    int prePos = clockHandle;
	    do{
	        clockHandle += 1;
	        if (clockHandle >= ppnToProcess.length) clockHandle = 0;
	        // TO DO: clock logic
			if(ppnToProcess[clockHandle].ref == false && ppnToProcess[clockHandle].pinned == false){
				free_pages.add(clockHandle);
				// invalid the entry in the process which owns the ppn now
				boolean isDirty = ppnToProcess[clockHandle].process.invalidVPN(ppnToProcess[clockHandle].vpn);
				if(isDirty){
					swapOut(clockHandle);
				}
				return true;
			}
			ppnToProcess[clockHandle].ref = false;
        }while(clockHandle != prePos);
	    //all pages are pinned;
	    return false;
    }

	public static int getFreePPN(VMProcess process, int vpn){
		if(free_pages.size() == 0){
		    while(!evictPage()){
		        //all pages are pinned, block current process.
            }
		}
		int ppn = free_pages.removeLast();
		ppnToProcess[ppn] = new Meta(process, vpn, true, false);
		return ppn;
	}

	public static void pin(int ppn){
		ppnToProcess[ppn].pinned = true;
	}

	public static void unpin(int ppn){
		ppnToProcess[ppn].pinned = false;
		//if there is a process blocked, wake it
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	// Meta info for inverted pageTable ppnToProcess
    public static class Meta{
        VMProcess process;
        boolean ref, pinned;
        int vpn;
        public Meta(VMProcess process, int vpn, boolean ref, boolean pinned){
            this.process = process;
            this.vpn = vpn;
            this.ref = ref;
            this.pinned = pinned;
        }
    }

    private static Meta[] ppnToProcess = new Meta[Machine.processor().getNumPhysPages()];

    private static int clockHandle = 0;

}
