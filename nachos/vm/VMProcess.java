package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.userprog.UserProcess;
import nachos.vm.*;

import java.util.HashMap;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		vpnToSection = new int[numPages][2];
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		System.out.println("Got in loadSections");
		vpnToSection = new int[numPages][2];
		// Begin mutex block
		UserKernel.page_lock.acquire();
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.page_lock.release();
			return false;
		}
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
			pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
		// load sections
		int temp_vpn, page_count, ppn;
		temp_vpn = 0;
		page_count = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				temp_vpn = vpn;
//				ppn = UserKernel.free_pages.removeLast();
//				used_free_pages.add(ppn);
				// Cannot assume virtual addresses == physical addresses
//				section.loadPage(i, ppn);

				vpnToSection[vpn] = new int[]{s, i};
				pageTable[page_count].vpn = vpn;
				pageTable[page_count].readOnly = section.isReadOnly();
				page_count++;
			}
		}
		int available_pages = 0;
		available_pages = numPages - page_count;
		temp_vpn++;
		for (int i = 0; i < available_pages; i++) {
			//ppn = UserKernel.free_pages.removeLast();
			pageTable[page_count + i].vpn = temp_vpn + i;
			// -1 means args or stack section.
			vpnToSection[temp_vpn + i] = new int[]{-1, i};
//			pageTable[page_count + i] = new TranslationEntry(temp_vpn + i, ppn, true, false, false, false);
		}
		UserKernel.page_lock.release();
		// End mutex block
		System.out.println("Exiting in loadSections");
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
//		System.out.println("Got in VM readVirtualMemory");
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// Multiprogramming modifications
		if (vaddr < 0) {
			System.out.println("vaddr < 0, returning 0");
			return 0;
		}

		int bytes_left = length;
		int paddr, paddr_offset, vpn;
		// Initialize physical address to -1
		paddr = -1;
		// Get the physical address offset
		paddr_offset = Processor.offsetFromAddress(vaddr);
		// Get virtual page number
		vpn = Processor.pageFromAddress(vaddr);
		// vpn exceed numPages ?
		if(vpn >= pageTable.length){
			System.out.println("vpn exceed numPages");
			return 0;
		}

		// the key of pageTable is vpn, so no need to loop the pageTable.
		if(!pageTable[vpn].valid) handlePageFault(vpn);
		VMKernel.pin(pageTable[vpn].ppn);
		VMKernel.ref(pageTable[vpn].ppn);
		paddr = pageTable[vpn].ppn * pageSize + paddr_offset;

		// Loop through our pageTable, find next valid page
//		for (int i = 0; i < pageTable.length; i++) {
//			if (pageTable[i].vpn == vpn) {
//				if(pageTable[i].valid == false) {
//					handlePageFault(vpn);
//				}
//				paddr = pageTable[i].ppn * pageSize + paddr_offset;
//				break;
//			}
//		}
		// Check physical address
		if (paddr < 0 || paddr >= memory.length) {
			System.out.println("paddr < 0 || paddr >= memory.length, returning 0");
			return 0;
		}
		int amount, total_amount;
		total_amount = 0;
		amount = Math.min(bytes_left, pageSize - paddr_offset);
		System.arraycopy(memory, paddr, data, offset, amount);
		bytes_left -= amount;
		offset += amount;
		total_amount += amount;
		boolean found = false;
		VMKernel.unpin(pageTable[vpn].ppn);
		vpn++;
		// Loop until error or the entire length is read
		while (bytes_left > 0) {
			// the key of pageTable is vpn, so no need to loop the pageTable.
			if(vpn >= pageTable.length){
				System.out.println("vpn exceed numPages");
				return 0;
			}
			if(!pageTable[vpn].valid) handlePageFault(vpn);
			VMKernel.pin(pageTable[vpn].ppn);
			VMKernel.ref(pageTable[vpn].ppn);
			paddr = pageTable[vpn].ppn * pageSize;
			found = true;

			// Loop through our pageTable, find next valid page
//			for (int i = 0; i < pageTable.length; i++) {
//				if (pageTable[i].vpn == vpn) {
//					if(pageTable[i].valid == false){
//						handlePageFault(vpn);
//					}
//					paddr = pageTable[i].ppn * pageSize;
//					found = true;
//					break;
//				}
//			}
			// Check validity
			if (paddr < 0 || paddr >= memory.length || !found) {
				System.out.println("paddr < 0 || paddr >= memory.length || !found, returning total_amount");
				return total_amount;
			}

			// Reset found
			found = false;
			amount = Math.min(bytes_left, pageSize);
			System.arraycopy(memory, paddr, data, offset, amount);
			bytes_left -= amount;
			offset += amount;
			total_amount += amount;
			VMKernel.unpin(pageTable[vpn].ppn);
			vpn++;
		}
//		System.out.println("Exiting VM readVirtualMemory");
		return total_amount;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
//		System.out.println("Got in VM writeVirtualMemory");
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// Multiprogramming modifications

		// Now we cannot assume virtual address == physical address
		if (vaddr < 0)
		{
			System.out.println("vaddr < 0, returning 0");
			return 0;
		}

		int bytes_left = length;
		int paddr, paddr_offset, vpn;
		// Initialize physical address to -1
		paddr = -1;
		// Get the physical address offset
		paddr_offset = Processor.offsetFromAddress(vaddr);
		// Get virtual page number
		vpn = Processor.pageFromAddress(vaddr);
		if(vpn >= pageTable.length){
			System.out.println("vpn exceed numPages");
			return 0;
		}
		// the key of pageTable is vpn, so no need to loop the pageTable.
		if(!pageTable[vpn].valid) handlePageFault(vpn);
		VMKernel.pin(pageTable[vpn].ppn);
		VMKernel.ref(pageTable[vpn].ppn);
		paddr = pageTable[vpn].ppn * pageSize + paddr_offset;

		// Loop through our pageTable, find next valid page
//		for (int i = 0; i < pageTable.length; i ++)
//		{
//			if (!pageTable[i].readOnly && pageTable[i].vpn == vpn)
//			{
//				if(!pageTable[i].valid) handlePageFault(pageTable[i].vpn);
//				paddr = pageTable[i].ppn * pageSize + paddr_offset;
//				break;
//			}
//		}
		// Check physical address
		if (paddr < 0 || paddr >= memory.length)
		{
			System.out.println("paddr < 0 || paddr >= memory.length, returning 0");
			return 0;
		}
		int amount, total_amount;
		total_amount = 0;
		amount = Math.min(bytes_left, pageSize - paddr_offset);
		System.arraycopy(data, offset, memory, paddr, amount);
		bytes_left -= amount;
		offset += amount;
		total_amount += amount;
		boolean found = false;
		VMKernel.unpin(pageTable[vpn].ppn);
		vpn++;
		// Loop until error or the entire length is read
		while (bytes_left > 0)
		{
			// the key of pageTable is vpn, so no need to loop the pageTable.
			if(vpn >= pageTable.length){
				System.out.println("vpn exceed numPages");
				return 0;
			}
			if(!pageTable[vpn].valid) handlePageFault(vpn);
			VMKernel.pin(pageTable[vpn].ppn);
			VMKernel.ref(pageTable[vpn].ppn);
			paddr = pageTable[vpn].ppn * pageSize;
			found = true;

			// Loop through our pageTable, find next valid page
//			for (int i = 0; i < pageTable.length; i ++)
//			{
//				if (!pageTable[i].readOnly && pageTable[i].vpn == vpn)
//				{
//					if(!pageTable[i].valid) handlePageFault(pageTable[i].vpn);
//					paddr = pageTable[i].ppn * pageSize;
//					found = true;
//					break;
//				}
//			}
			// Check validity
			if (paddr < 0 || paddr >= memory.length || !found)
			{
				System.out.println("paddr < 0 || paddr >= memory.length || !found, returning total_amount");
				return total_amount;
			}

			// Reset found
			found = false;
			amount = Math.min(bytes_left, pageSize);
			System.arraycopy(data, offset, memory, paddr, amount);
			bytes_left -= amount;
			offset += amount;
			total_amount += amount;
			VMKernel.pin(pageTable[vpn].ppn);
			vpn++;
		}
//		System.out.println("Exiting VM writeVirtualMemory");
		return total_amount;
	}

	/**
	 * Handle page fault
	 */
	protected void handlePageFault(int vpn){
		TranslationEntry entry = pageTable[vpn];
		int sectionNum = vpnToSection[vpn][0], i = vpnToSection[vpn][1];
		int ppn = VMKernel.getFreePPN(this, vpn);
//		System.out.println("handle page fault: " + vpn);
//		System.out.println(sectionNum + " " + i + " " + ppn);
		if(sectionNum == -1){
			// load a stack or args page
			byte[] mem = Machine.processor().getMemory();
			byte[] buf = new byte[pageSize];
			System.arraycopy(buf, 0, mem, ppn*pageSize, pageSize);
		}else{
			// load a page from coff
			CoffSection section = coff.getSection(sectionNum);
			section.loadPage(i, ppn);
		}
		// why used_free_page not in kernel but in process?
		used_free_pages.add(ppn);
		entry.ppn = ppn;
		entry.valid = true;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionPageFault:
				Lib.debug(dbgProcess, "Pagefault in handleException!");
				int addr = processor.readRegister(processor.regBadVAddr);
            	handlePageFault(processor.pageFromAddress(addr));
            	break;
			default:
				super.handleException(cause);
				break;
		}
	}

	// invalid the vpn in pageTable, return isDirty
	public boolean invalidVPN(int vpn){
		pageTable[vpn].valid = false;
		return pageTable[vpn].dirty;
    }

    public UThread getThread(){
		return this.thread;
	}

	private static final int pageSize = Processor.pageSize;

	private int[][] vpnToSection;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
