package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
		/**
		 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
		 * alarm's callback.
		 * 
		 * <p>
		 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
		 */
		public Alarm() {
			Machine.timer().setInterruptHandler(new Runnable() {
				public void run() {
					timerInterrupt();
				}
			});
		}
	
		/**
		 * The timer interrupt handler. This is called by the machine's timer
		 * periodically (approximately every 500 clock ticks). Causes the current
		 * thread to yield, forcing a context switch if there is another thread that
		 * should be run.
		 */
		public void timerInterrupt() {
			// Check if the queue is empty
			if (wait_queue.size() == 0)
				return;
			// Now we check the head thread in our queue, if it is its time
			// to wake up, we move it to the ready queue and cause current 
			// thread to yield
			KThread head = wait_queue.peek();
			
			if (head.getWakeTime() < Machine.timer().getTime())
			{
				wait_queue.remove(head);
				head.ready();
			}
			KThread.currentThread().yield();
		}
	
		/**
		 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
		 * in the timer interrupt handler. The thread must be woken up (placed in
		 * the scheduler ready set) during the first timer interrupt where
		 * 
		 * <p>
		 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
		 * 
		 * @param x the minimum number of clock ticks to wait.
		 * 
		 * @see nachos.machine.Timer#getTime()
		 */
		public void waitUntil(long x) {
			// for now, cheat just to get something working (busy waiting is bad)
			
			// If the wait parameter x is 0 or negative, return without waiting
			if (x <= 0)
				return;
			// Get current_thread, update its wake_time
			KThread current_thread = KThread.currentThread();
			long wakeTime = Machine.timer().getTime() + x;
			current_thread.setWakeTime(wakeTime);
			// Add current thread to our wait queue
			wait_queue.add(current_thread);
			// Disable interrupt
			boolean state = Machine.interrupt().disable();
			current_thread.sleep();
			Machine.interrupt().restore(state);
		}
	
	        /**
		 * Cancel any timer set by <i>thread</i>, effectively waking
		 * up the thread immediately (placing it in the scheduler
		 * ready set) and returning true.  If <i>thread</i> has no
		 * timer set, return false.
		 * 
		 * <p>
		 * @param thread the thread whose timer should be cancelled.
		 */
	    public boolean cancel(KThread thread) {
	        // If thread has no timer set, return false
	        if (thread.getWakeTime() == 0)
	        	return false;
	        // Reset timer, place it to ready queue
	        thread.setWakeTime(0);
	        thread.ready();
	        return true;
		}
        
        public static void alarmTest1() {
        	int durations[] = {1000, 10*1000, 100*1000};
        	long t0, t1;

        	for (int d : durations) {
        	    t0 = Machine.timer().getTime();
        	    ThreadedKernel.alarm.waitUntil (d);
        	    t1 = Machine.timer().getTime();
        	    System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
        	}
        }
        public static void selfTest() {
        	alarmTest1();

        	// Invoke your other test methods here ...
        }
        private PriorityQueue<KThread> wait_queue = new PriorityQueue<KThread>(new thread_comparator());
}

/**
 * Comparator used for Alarm class priority queue
 * May use it for other later
 */
class thread_comparator implements Comparator<KThread> 
{
	public int compare(KThread thread1, KThread thread2)
	{
			return Long.compare(thread1.getWakeTime(), thread2.getWakeTime());
	}
}
