package jrds.probe;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jrds.Probe;
import jrds.RdsHost;
import jrds.Starter;
import jrds.agent.RProbe;

import org.apache.log4j.Logger;

public class RMI extends Probe {
	static final private Logger logger = Logger.getLogger(RMI.class);
	static final private int PORT = 2002;
	//We want to have socket with a short default timeout
	static final private RMIClientSocketFactory sf = new RMIClientSocketFactory() {
		public Socket createSocket(String host, int port) throws IOException {
			Socket s = new Socket(host, port) {
				public void connect(SocketAddress endpoint) throws IOException {
					super.connect(endpoint, TIMEOUT * 1000);
				}
			};
			s.setSoTimeout(TIMEOUT * 1000);
			return s;
		}
	};
	List<?> args = new ArrayList<Object>(0);
	private String remoteName = null;
	static {
		System.setProperty("java.rmi.server.disableHttp", "true");
		System.setProperty("sun.rmi.transport.tcp.readTimeout", Long.toString(TIMEOUT * 1000));
		System.setProperty("sun.rmi.transport.tcp.handshakeTimeout", Long.toString(TIMEOUT * 1000));
		System.setProperty("sun.rmi.transport.connectionTimeout", Long.toString(TIMEOUT * 1000));
	}

	private class RMIStarter extends Starter {
		private RdsHost host;
		private Starter resolver = null;
		private int port = PORT;
		private Registry registry = null;
		private RProbe rp = null;
		RMIStarter(RdsHost host) {
			this.host = host;
			resolver = host.addStarter(new Starter.Resolver(host.getName()));
		}
		RMIStarter(RdsHost host, int port) {
			this.host = host;
			this.port = port;
			resolver = host.addStarter(new Starter.Resolver(host.getName()));
		}
		@Override
		public Object getKey() {
			return "rmi://" + host + ":"  + port;
		}
		@Override
		public boolean start() {
			boolean started = false;
			if(resolver.isStarted()) {
				try {
					registry = LocateRegistry.getRegistry(host.getName(), port, sf);
					rp = (RProbe) registry.lookup(RProbe.NAME);
					started = true;
				} catch (RemoteException e) {
					logger.error("Remote exception on server " + host + ": " + e.getCause());
				} catch (NotBoundException e) {
					logger.error("Unattended exception: ", e);
				}
			}
			return started;
		}
		@Override
		public void stop() {
			rp = null;
			registry = null;
		}
	};
	RMIStarter localstarter = null;

	public RMI() {
	}

	public void setHost(RdsHost monitoredHost) {
		localstarter = (RMIStarter) monitoredHost.addStarter(new RMIStarter(monitoredHost, PORT));
		super.setHost(monitoredHost);
	}

	protected  RProbe init() {
		RProbe rp = null;
		try {
			rp = (RProbe) localstarter.rp;
			if( ! rp.exist(remoteName))
				remoteName = rp.prepare(getSpecific(), args);
		} catch (RemoteException e) {
			rp = null;
			logger.error("Remote exception on server with probe " + this + ": " + e.getCause());
		}
		return rp;
	}

	@Override
	public Map getNewSampleValues() {
		Map retValues = new HashMap(0);
		if(localstarter.isStarted()) {
			RProbe rp = init();
			try {
				if(rp != null)
					retValues = rp.query(remoteName);
			} catch (RemoteException e) {
				logger.error("Remote exception on server with probe " + this + ": " + e.getCause());
			}
		}
		return retValues;
	}

	public List<?> getArgs() {
		return args;
	}

	public void setArgs(List<?> l) {
		this.args = l;
	}
	@Override
	public boolean isStarted() {
		return localstarter.isStarted();
	}

	@Override
	public String getSourceType() {
		return "JRDS Agent";
	}
}
