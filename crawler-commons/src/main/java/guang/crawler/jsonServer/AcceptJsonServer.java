package guang.crawler.jsonServer;

import guang.crawler.localConfig.ComponentLoader;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 该类代表使用Accept机制的Socket服务器，使用JSON协议进行通信。
 *
 * @author yang
 */
public class AcceptJsonServer implements Runnable, JsonServer {
	
	/**
	 * 底层的服务器套接字
	 */
	private final ServerSocket	        server;
	/**
	 * 服务器使用的线程池,从而重复利用线程
	 */
	private final ExecutorService	    threadPool;
	/**
	 * Commandlet的加载器
	 */
	private ComponentLoader<Commandlet>	commandletLoader;
	/**
	 * 服务器线程
	 */
	private Thread	                    serverThread;
	/**
	 * 线程控制器
	 */
	private AcceptThreadController	    acceptThreadController;
	
	/**
	 * 创建一个基于Accept机制的JSON服务器,将直接选定一个未被使用的端口监听外部连接.
	 *
	 * @param threadNum
	 *            需要的线程的数量
	 * @param configFile
	 *            配置文件
	 * @param schemaFile
	 *            XSD模板文件
	 * @throws ServerStartException
	 */
	public AcceptJsonServer(final int threadNum, final File configFile,
	        final File schemaFile) throws ServerStartException {
		
		this.acceptThreadController = new AcceptThreadController();
		this.commandletLoader = new ComponentLoader<Commandlet>(configFile,
		        schemaFile);
		try {
			this.commandletLoader.load();
		} catch (Exception e) {
			throw new ServerStartException("Load config file failed!", e);
		}
		
		try {
			this.server = new ServerSocket();
		} catch (IOException e) {
			throw new ServerStartException("Can not open socket!", e);
		}
		this.threadPool = Executors.newFixedThreadPool(threadNum);
		
	}
	
	/**
	 * 利用指定的参数创建一个JSON服务器
	 *
	 * @param port
	 *            在指定的端口上监听.
	 * @param backlog
	 *            服务器套接字等待队列的大小
	 * @param threadNum
	 *            线程的数量
	 * @param configFile
	 *            配置文件
	 * @param schemaFile
	 *            XSD模板文件
	 * @throws ServerStartException
	 */
	public AcceptJsonServer(final int port, final int backlog,
	        final int threadNum, final File configFile, final File schemaFile)
	        throws ServerStartException {
		
		this.acceptThreadController = new AcceptThreadController();
		this.commandletLoader = new ComponentLoader<Commandlet>(configFile,
		        schemaFile);
		try {
			this.commandletLoader.load();
		} catch (Exception e) {
			throw new ServerStartException("Load config file failed!", e);
		}
		
		try {
			this.server = new ServerSocket(port, backlog);
		} catch (IOException e) {
			throw new ServerStartException("Can not open socket!", e);
		}
		this.threadPool = Executors.newFixedThreadPool(threadNum);
		
	}
	
	@Override
	public InetAddress getAddress() {
		if (this.server != null) {
			return this.server.getInetAddress();
		}
		return null;
	}
	
	@Override
	public int getPort() {
		if (this.server != null) {
			return this.server.getLocalPort();
		}
		return 0;
	}
	
	@Override
	public boolean isShutdown() {
		if (this.acceptThreadController.getType() != AcceptThreadController.TYPE_START) {
			return true;
		} else if (this.server.isClosed() && this.threadPool.isTerminated()) {
			return true;
		}
		return false;
	}
	
	/**
	 * 服务器的主线程
	 */
	@Override
	public void run() {
		while (this.acceptThreadController.getType() == AcceptThreadController.TYPE_START) {
			Socket client;
			try {
				client = this.server.accept();
				AcceptRequestHandler command = new AcceptRequestHandler(client,
				        this.commandletLoader, this.acceptThreadController);
				this.threadPool.submit(command);
			} catch (IOException ex) {
				// 在accept的时候断掉了，说明是系统要求线程停止了。
				break;
			}
		}
		// 在这里已经结束了，被要求停止
		try {
			this.server.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		this.threadPool.shutdownNow();
		
	}
	
	@Override
	public void shutdown() {
		this.acceptThreadController.setType(AcceptThreadController.TYPE_SHUTDOWN_NOW);
		try {
			this.server.close();
		} catch (IOException e) {
			// Should not come here.
			e.printStackTrace();
		}
		
	}
	
	@Override
	public boolean start() {
		if (this.serverThread == null) {
			this.serverThread = new Thread(this);
		}
		if ((this.serverThread != null) && !this.serverThread.isAlive()) {
			try {
				this.serverThread.start();
				return true;
			} catch (IllegalThreadStateException e) {
				return false;
			}
		}
		return false;
	}
	
	@Override
	public void waitForStop() {
		if (this.serverThread.isAlive()) {
			try {
				this.serverThread.join();
				this.threadPool.awaitTermination(1, TimeUnit.HOURS);
			} catch (InterruptedException e) {
				return;
			}
		}
	}
	
}
