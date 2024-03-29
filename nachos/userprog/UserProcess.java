package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
//		int numPhysPages = Machine.processor().getNumPhysPages();
//		pageTable = new TranslationEntry[numPhysPages];
//		for (int i = 0; i < numPhysPages; i++)
//			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		
		// Begin mutex block
		UserKernel.process_lock.acquire();
		// Assign PID
		pid = UserKernel.pid;
		UserKernel.pid ++;
		UserKernel.process_num ++;
		UserKernel.process_lock.release();
		// End mutex block
		
		unhandled = false;
		used_free_pages = new LinkedList<Integer>();
		// This holds all active running child processes of this process
		children = new HashMap<Integer, UserProcess>();
		children_exit_status = new HashMap<Integer, Integer>();
		// This holds all children of the process
		all_children = new LinkedList<Integer>();
		lock = new Lock();
		cv = new Condition(lock);
		// Initialize file table
		file_table = new OpenFile[table_size];
		// File descriptors 0 and 1 must refer to standard input and standard output.
		file_table[0] = UserKernel.console.openForReading();
		file_table[1] = UserKernel.console.openForWriting();
		// May have to modify StubFileSystem openfile count
		// Ask TA about this.
		// Initialize the rest of files to null
		for (int i = 2; i < file_table.length; i ++)
			file_table[i] = null;
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		System.out.println("Got in readVirtualMemory");
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		
		// for now, just assume that virtual addresses equal physical addresses
//		if (vaddr < 0 || vaddr >= memory.length)
//			return 0;
		
		// Multiprogramming modifications
		
		// Cannot assume virtual addresses == physical addresses 
		if (vaddr < 0)
		{
			System.out.println("vaddr < 0, returning 0");
			return 0;
		}
		
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy(memory, vaddr, data, offset, amount);
		
		int bytes_left = length;
		int paddr, paddr_offset, vpn;
		// Initialize physical address to -1
		paddr = -1;
		// Get the physical address offset
		paddr_offset = Processor.offsetFromAddress(vaddr);
		// Get virtual page number
		vpn = Processor.pageFromAddress(vaddr);
		// Loop through our pageTable, find next valid page
		for (int i = 0; i < pageTable.length; i ++)
		{
			if (pageTable[i].valid && pageTable[i].vpn == vpn)
			{
				paddr = pageTable[i].ppn * pageSize + paddr_offset;
				break;
			}
		}
		// Check physical address
		if (paddr < 0 || paddr >= memory.length)
		{
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
		vpn ++;
		// Loop until error or the entire length is read
		while (bytes_left > 0)
		{
			// Loop through our pageTable, find next valid page
			for (int i = 0; i < pageTable.length; i ++)
			{
				if (pageTable[i].valid && pageTable[i].vpn == vpn)
				{
					paddr = pageTable[i].ppn * pageSize;
					found = true;
					break;
				}
			}
			// Check validity 
			if (paddr < 0 || paddr >= memory.length || !found)
			{
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
			vpn ++;
		}
		System.out.println("Exiting readVirtualMemory");
		return total_amount;
		
//		Lib.assertTrue(offset >= 0 && length >= 0
//				&& offset + length <= data.length);
//
//		byte[] memory = Machine.processor().getMemory();
//		int virtualPage = Machine.processor().pageFromAddress(vaddr);
//		pageTable[virtualPage].used = true;
//
//		int phyPage = pageTable[virtualPage].ppn;
//		int memoryOffset = Machine.processor().offsetFromAddress(vaddr);
//		int phyAddress = phyPage * pageSize + memoryOffset;
//
//		// for now, just assume that virtual addresses equal physical addresses
//		if (phyAddress < 0 || phyAddress >= memory.length)
//			return 0;
//
//		int amount = Math.min(length, memory.length - phyAddress);
//		System.arraycopy(memory, phyAddress, data, offset, amount);
//
//		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		System.out.println("Got in writeVirtualMemory");
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
		// Loop through our pageTable, find next valid page
		for (int i = 0; i < pageTable.length; i ++)
		{
			if (pageTable[i].valid && !pageTable[i].readOnly && pageTable[i].vpn == vpn)
			{
				paddr = pageTable[i].ppn * pageSize + paddr_offset;
				break;
			}
		}
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
		vpn ++;
		// Loop until error or the entire length is read
		while (bytes_left > 0)
		{
			// Loop through our pageTable, find next valid page
			for (int i = 0; i < pageTable.length; i ++)
			{
				if (pageTable[i].valid && !pageTable[i].readOnly && pageTable[i].vpn == vpn)
				{
					paddr = pageTable[i].ppn * pageSize;
					found = true;
					break;
				}
			}
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
			vpn ++;
		}
		System.out.println("Exiting writeVirtualMemory");
		return total_amount;
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy(data, offset, memory, vaddr, amount);
//
//		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		System.out.println("Got in loadSections");
		// Begin mutex block
		UserKernel.page_lock.acquire();
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.page_lock.release();
			return false;
		}
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i ++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
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
				ppn = UserKernel.free_pages.removeLast();
				used_free_pages.add(ppn);
				// Cannot assume virtual addresses == physical addresses
				section.loadPage(i, ppn);
				if (!section.isReadOnly())
					//pageTable[page_count].readOnly = true;
					pageTable[page_count] = new TranslationEntry(vpn, ppn, true, false, false, false);
				else
					pageTable[page_count] = new TranslationEntry(vpn, ppn, true, true, false, false);
				page_count ++;
			}
		}
		int available_pages = 0;
		available_pages = numPages - page_count;
		temp_vpn ++;
		for (int i = 0; i < available_pages; i ++)
		{
			ppn = UserKernel.free_pages.removeLast();
			pageTable[page_count + i] = new TranslationEntry(temp_vpn + i, ppn, true, false, false, false);
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
		System.out.println("Got in unloadSections");
		// Begin mutex block
		UserKernel.page_lock.acquire();
		while (!used_free_pages.isEmpty())
		{
			int page = used_free_pages.removeLast();
			UserKernel.free_pages.add(page);
		}
		UserKernel.page_lock.release();
		// End mutex block
		System.out.println("Exiting unloadSections");
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		// Modify the function so that only the root process can call on it
		if (pid != 0)
		{
			System.out.println("Non root process called Halt(), returning -1");
			return -1;
		}
		
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	    // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		unloadSections();
		coff.close();
		// Reset file table
		for (int i = 0; i < table_size; i ++)
		{
			if (file_table[i] != null)
			{
				file_table[i].close();
				file_table[i] = null;
			}
		}
		// Now we need to reset this process's children's parent pointer
		for (UserProcess child : children.values())
			child.parent = null;
		// Remove this process from its parent's children if necessary
		if (parent != null)
		{
			parent.children.remove(pid);
			//parent.children.replace(pid, null);
			if (unhandled)
				parent.children_exit_status.put(pid, null);
			else
				parent.children_exit_status.put(pid, status);
			// Begin mutex block
			lock.acquire();
			cv.wake();
			lock.release();
		}
		
		// Begin mutex block
		UserKernel.process_lock.acquire();
		if (UserKernel.process_num > 1)
		{
			UserKernel.process_num --;
			UserKernel.process_lock.release();
			KThread.currentThread().finish();
		}
		// Last active process
		else
		{
			UserKernel.process_num --;
			UserKernel.process_lock.release();
			Kernel.kernel.terminate();
			KThread.currentThread().finish();
		}
		// End mutex block
		
		
		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process
		// Kernel.kernel.terminate();

		return 0;
	}
	
	/**
	 * Execute the program stored in the specified file, with the specified
	 * arguments, in a new child process. The child process has a new unique
	 * process ID, and starts with stdin opened as file descriptor 0, and stdout
	 * opened as file descriptor 1.
	 *
	 * file is a null-terminated string that specifies the name of the file
	 * containing the executable. Note that this string must include the ".coff"
	 * extension.
	 *
	 * argc specifies the number of arguments to pass to the child process. This
	 * number must be non-negative.
	 *
	 * argv is an array of pointers to null-terminated strings that represent the
	 * arguments to pass to the child process. argv[0] points to the first
	 * argument, and argv[argc-1] points to the last argument.
	 *
	 * exec() returns the child process's process ID, which can be passed to
	 * join(). On error, returns -1.
	 */
	private int handleExec(int file_addr, int argc, int argv)
	{
		if (argc < 0)
		{
			System.out.println("argc < 0, returning -1");
			return -1;
		}
		// Get the string for the file name
		String file = readVirtualMemoryString(file_addr, maxStrLen);
		if (file == null || file.length() < 6)
		{
			// .coff is of size 5, valid file name must include this extension
			// therefore the shortest valid file name must be greater than 5
			System.out.println("file == null || file.length() < 6, returning -1");
			return -1;
		}
		// Check if .coff exists in the file name
		String coff_extension = file.substring(file.length() - 5);
		if (!coff_extension.equals(".coff"))
		{
			System.out.println("file name does not end with .coff extension, returning -1");
			return -1;
		}
		// Extract arguments
		int vaddr = -1;
		String[] args = new String[argc];
		for (int i = 0; i < argc; i ++)
		{
			byte[] vaddr_pointer = new byte[4];
			readVirtualMemory(argv + i * 4, vaddr_pointer);
			vaddr = Lib.bytesToInt(vaddr_pointer, 0);
			// Extract argument i
			args[i] = readVirtualMemoryString(vaddr, maxStrLen);
			if (args[i] == null)
			{
				System.out.println("args[i] == null, returning -1");
				return -1;
			}
		}
		UserProcess child = newUserProcess();
		boolean executed = child.execute(file, args);
		if (!executed)
		{
			// If child process executes failed, do we have to do anything other
			// than decrease the running process number?
			// Begin mutex block
			System.out.println("Child process not executed");
			UserKernel.process_lock.acquire();
			System.out.println("Child process not executed, returning -1");
			UserKernel.process_num --;
			UserKernel.process_lock.release();
			return -1;
		}
		else
		{
			System.out.println("Child process executed");
			child.parent = this;
			children.put(child.pid, child);
			all_children.add(child.pid);
			return child.pid;
		}
		
	}
	
	/**
	 * Suspend execution of the current process until the child process specified
	 * by the processID argument has exited. If the child has already exited by the
	 * time of the call, returns immediately. When the current process resumes, it
	 * disowns the child process, so that join() cannot be used on that process
	 * again.
	 *
	 * processID is the process ID of the child process, returned by exec().
	 *
	 * status points to an integer where the exit status of the child process will
	 * be stored. This is the value the child passed to exit(). If the child exited
	 * because of an unhandled exception, the value stored is not defined.
	 *
	 * If the child exited normally, returns 1. If the child exited as a result of
	 * an unhandled exception, returns 0. If processID does not refer to a child
	 * process of the current process, returns -1.
	 */
	private int handleJoin(int processID, int status)
	{
		// First check if the given processID is one of the process's children
		//if (!children.containsKey(processID))
		if (!all_children.contains(processID))
		{
			System.out.println("Process is not one of the children process, returning -1");
			return -1;
		}
		
		
		//Failed attempt to use KThread.join()
		/*
		 UThread parent = new UThread(this);
		 UThread child = new UThread(children.get(processID));
		 System.out.println("Before join on child");
		 child.join();
		 System.out.println("After join on child");
		*/
		
		if (children_exit_status.containsKey(processID))
		{
			if (children_exit_status.get(processID) != null)
			{
				// The child process exit normally
				// Need to transfer the child's exit status to its parent
				byte[] buffer = Lib.bytesFromInt(children_exit_status.get(processID));
				writeVirtualMemory(status, buffer);
				return 1;
			}
			else
			{
				// Child did not exit normally, unhandled exception happened
				return 0;
			}
		}
		else
		{
			cv.sleep();
		}
		
		
//		while (!children_exit_status.containsKey(processID))
//		{
//			
//		}
		System.out.println("Join should not reach here, returning -1");
		return -1;
	}
	
	/**
	 * Attempt to open the named disk file, creating it if it does not exist,
	 * and return a file descriptor that can be used to access the file. If
	 * the file already exists, creat truncates it.
	 *
	 * Note that creat() can only be used to create files on disk; creat() will
	 * never return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */
	private int handleCreat(int vaddr)
	{
		// Get the string for the file name
		String file = readVirtualMemoryString(vaddr, maxStrLen);
		if (file == null)
		{
			System.out.println("Error: file == null, returning -1");
			return -1;
		}
		int file_descriptor = -1;
		// Loop through the file table to find a next available open file
		for (int i = 2; i < file_table.length; i ++)
		{
			if (file_table[i] == null)
			{
				file_descriptor = i;
				break;
			}
		}
		if (file_descriptor == -1)
		{
			System.out.println("Error: file_descriptor == -1, no avaiable open file, returning -1");
			return -1;
		}
		// Check if it is okay to access the given file
		OpenFile temp = ThreadedKernel.fileSystem.open(file, true);
		if (temp == null)
		{
			System.out.println("Error: ThreadedKernel.fileSystem.open(file, true) returned null, can not access given file, returning -1");
			return -1;
		}
		// Connect the open file with our file table
		file_table[file_descriptor] = temp;
		return file_descriptor;
		// Do we need to close the file after it is created?
		// Ask TA about this
	}
	
	/**
	 * Attempt to open the named file and return a file descriptor.
	 *
	 * Note that open() can only be used to open files on disk; open() will never
	 * return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */
	private int handleOpen(int vaddr)
	{
		// Basically the same implementation as handleCreat, except we are not
		// creating a new file
		// Get the string for the file name
		String file = readVirtualMemoryString(vaddr, maxStrLen);
		if (file == null)
		{
			System.out.println("Error: file == null, returning -1");
			return -1;
		}
		int file_descriptor = -1;
		// Loop through the file table to find a next available open file
		for (int i = 2; i < file_table.length; i ++)
		{
			if (file_table[i] == null)
			{
				file_descriptor = i;
				break;
			}
		}
		if (file_descriptor == -1)
		{
			System.out.println("Error: file_descriptor == -1, no avaiable open file, returning -1");
			return -1;
		}
		// Check if it is okay to access the given file
		// Note we are not creating a new file here
		OpenFile temp = ThreadedKernel.fileSystem.open(file, false);
		if (temp == null)
		{
			System.out.println("Error: ThreadedKernel.fileSystem.open(file, true) returned null, can not access given file, returning -1");
			return -1;
		}
		// Connect the open file with our file table
		file_table[file_descriptor] = temp;
		return file_descriptor;
	}
	
	/**
	 * Attempt to read up to count bytes into buffer from the file or stream
	 * referred to by fileDescriptor.
	 *
	 * On success, the number of bytes read is returned. If the file descriptor
	 * refers to a file on disk, the file position is advanced by this number.
	 *
	 * It is not necessarily an error if this number is smaller than the number of
	 * bytes requested. If the file descriptor refers to a file on disk, this
	 * indicates that the end of the file has been reached. If the file descriptor
	 * refers to a stream, this indicates that the fewer bytes are actually
	 * available right now than were requested, but more bytes may become available
	 * in the future. Note that read() never waits for a stream to have more data;
	 * it always returns as much as possible immediately.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is read-only or
	 * invalid, or if a network stream has been terminated by the remote host and
	 * no more data is available.
	 */
	private int handleRead(int fileDescriptor, int vaddr, int count)
	{
		// Check if fileDescriptor and count are valid
		if (fileDescriptor < 0 || fileDescriptor > table_size - 1 || file_table[fileDescriptor] == null || count < 0)
		{
			System.out.println("Error: fileDescriptor < 0 || fileDescriptor > table_size - 1 || file_table[fileDescriptor] == null || count < 0, returning -1");
			return -1;
		}
		// Multiple reads in case of larger files
		int bytesLeft = count;
		int totalBytesRead = 0;
		while (bytesLeft > 0)
		{
			// Page sized read
			byte[] page_buffer = new byte[pageSize];
			int bytesToRead = Math.min(pageSize, bytesLeft);
			int bytesRead = file_table[fileDescriptor].read(page_buffer, 0, bytesToRead);
			// Check for read failure
			if (bytesRead == -1)
			{
				System.out.println("Error: bytesRead == -1, returning -1");
				return -1;
			}
			// Check if there is no bytes left to read
			if (bytesRead == 0)
				break;
			int bytesWritten = writeVirtualMemory(vaddr, page_buffer, 0, bytesRead);
			// Check if the amount of bytes read is the same as the amount of bytes written
			if (bytesRead != bytesWritten)
			{
				System.out.println("Error: bytesRead != bytesWritten, returning -1");
				return -1;
			}
			// Update bytes variables
			bytesLeft -= bytesRead;
			totalBytesRead += bytesRead;
			// Update vaddr
			vaddr += bytesRead;
		}
		return totalBytesRead;
	}
	
	/**
	 * Attempt to write up to count bytes from buffer to the file or stream
	 * referred to by fileDescriptor. write() can return before the bytes are
	 * actually flushed to the file or stream. A write to a stream can block,
	 * however, if kernel queues are temporarily full.
	 *
	 * On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number. It IS an
	 * error if this number is smaller than the number of bytes requested. For
	 * disk files, this indicates that the disk is full. For streams, this
	 * indicates the stream was terminated by the remote host before all the data
	 * was transferred.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
	 * if a network stream has already been terminated by the remote host.
	 */
	private int handleWrite(int fileDescriptor, int vaddr, int count)
	{
		// Check if fileDescriptor and count are valid
		if (fileDescriptor < 0 || fileDescriptor > table_size - 1 || file_table[fileDescriptor] == null || count < 0)
		{
			System.out.println("Error: fileDescriptor < 0 || fileDescriptor > table_size - 1 || file_table[fileDescriptor] == null || count < 0, returning -1");
			return -1;
		}
		// Multiple writes in case of larger files
		int totalBytesWritten = 0;
		int bytesLeft = count;
		while (bytesLeft > 0)
		{
			// Page sized read
			byte[] page_buffer = new byte[pageSize];
			int bytesToWrite = Math.min(pageSize, bytesLeft);
			int bytesRead = readVirtualMemory(vaddr, page_buffer, 0, bytesToWrite);
			int bytesWritten = file_table[fileDescriptor].write(page_buffer, 0, bytesRead);
			if (bytesWritten == -1)
			{
				System.out.println("Error: bytesWritten == -1, returning -1");
				return -1;
			}
			if (bytesRead != bytesWritten || bytesWritten != bytesToWrite)
			{
				System.out.println("Error: bytesRead != bytesWritten || bytesWritten != bytesToWrite, returning -1");
				return -1;
			}
			// Update bytes variables
			bytesLeft -= bytesWritten;
			totalBytesWritten += bytesWritten;
			// Update vaddr
			vaddr += bytesWritten;
		}
		// Check if bytes written is smaller than the number of bytes requested
		if (totalBytesWritten < count)
		{
			System.out.println("Error: totalBytesWritten < count, returning -1");
			return -1;
		}
		return totalBytesWritten;
	}
	
	/**
	 * Close a file descriptor, so that it no longer refers to any file or
	 * stream and may be reused. The resources associated with the file
	 * descriptor are released.
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 */
	private int handleClose(int fileDescriptor)
	{
		// Check if fileDescriptor is valid
		if (fileDescriptor < 0 || fileDescriptor > table_size - 1 || file_table[fileDescriptor] == null)
		{
			System.out.println("Error: fileDescriptor < 0 || fileDescriptor > table_size - 1 || file_table[fileDescriptor], returning -1");
			return -1;
		}
		file_table[fileDescriptor].close();
		file_table[fileDescriptor] = null;
		return 0;
	}
	
	/**
	 * Delete a file from the file system. 
	 *
	 * If another process has the file open, the underlying file system
	 * implementation in StubFileSystem will cleanly handle this situation
	 * (this process will ask the file system to remove the file, but the
	 * file will not actually be deleted by the file system until all
	 * other processes are done with the file).
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 */
	private int handleUnlink(int vaddr)
	{
		// Get the string for the file name
		String file = readVirtualMemoryString(vaddr, maxStrLen);
		if (file == null)
		{
			System.out.println("Error: file == null, returning -1");
			return -1;
		}
		boolean removed = ThreadedKernel.fileSystem.remove(file);
		if (!removed)
		{
			System.out.println("Error: file is not removed, returning -1");
			return -1;
		}
		return 0;
	}
	

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallCreate:
			return handleCreat(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			unhandled = true;
			// Should I handle this here?
			handleExit(-1);
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
	
	//Added variables
	private static final int maxStrLen = 256;
	private static final int table_size = 16;
	private OpenFile[] file_table;
	private int pid;
	private UserProcess parent = null;
	private HashMap<Integer, UserProcess> children;
	private HashMap<Integer, Integer> children_exit_status;
	public LinkedList<Integer> used_free_pages;
	public LinkedList<Integer> all_children;
	public static Lock lock;
	public static Condition cv;
	private boolean unhandled;
}
