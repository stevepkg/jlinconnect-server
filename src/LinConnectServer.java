import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.StringTokenizer;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.swing.ImageIcon;

public class LinConnectServer {

	private final int PORT = 9090;

	private final String NOTIF_HEADER_LBL = "notifheader:";

	private final String NOTIF_DESC_LBL = "notifdescription";

	private TrayIcon trayIcon;

	private boolean runningServer = true;

	private JmDNS jmdns;

	private String ipAdd = "";

	private ServerSocket serverSocket;

	class MultipleSocketServer implements Runnable {
		private Socket socket;

		MultipleSocketServer(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			try {

				DataInputStream in = new DataInputStream(
						socket.getInputStream());

				byte[] bytes = new byte[socket.getReceiveBufferSize()];

				// System.out.println("ReceiveBufferSize:" + bytes.length);

				int len = in.read(bytes);

				String content = null;

				boolean recievedNotification = false;

				if (len > 0) {

					String notifheader = null;
					String notifdescription = null;
					content = new String(bytes);
					StringTokenizer st = new StringTokenizer(content, "\n\r");

					while (st.hasMoreTokens() == true) {
						String token = st.nextToken().trim();

						// System.out.println("Content: " + token);

						int index = token.indexOf(NOTIF_HEADER_LBL);

						if (index != -1) {
							notifheader = token.substring(
									NOTIF_HEADER_LBL.length()).trim();
						}

						index = token.indexOf(NOTIF_DESC_LBL);

						if (index != -1) {
							notifdescription = token.substring(
									NOTIF_DESC_LBL.length()).trim();
						}
					}

					// System.out.println("Content:\n" + content);

					if (notifheader != null && notifdescription != null) {
						// System.out.println(notifheader + "\n\n"
						// + notifdescription);
						trayIcon.displayMessage(notifheader, notifdescription,
								TrayIcon.MessageType.INFO);
						recievedNotification = true;
					}
				}

				if (recievedNotification == false) {

					InputStream is = this.getClass().getResourceAsStream(
							"index.html");
					BufferedInputStream bis = new BufferedInputStream(is);

					ByteArrayOutputStream baos = new ByteArrayOutputStream();

					byte[] buff = new byte[1024];

					int pos = bis.read(buff);

					while (pos != -1) {
						baos.write(buff, 0, pos);
						pos = bis.read(buff);
					}

					bis.close();
					String page = new String(baos.toByteArray(), "UTF-8");

					page = page.replace("r%s", "r3");
					page = page.replace("%s", ipAdd + ":9090");

					OutputStream out = socket.getOutputStream();
					String response = "HTTP/1.0\n\r";
					response = response + "Date: " + new Date() + "\n\r";
					response = response + "Content-Type: text/html\n\r";
					response = response + "Content-Language:: en_AU\n\r";
					response = response + "Content-Length: "
							+ page.getBytes().length + "\n\r";
					response = response + page;

					out.write(response.getBytes());
					out.flush();
					out.close();

				} else {

					String resMsg = "<html><body>true</body></html>";
					OutputStream out = socket.getOutputStream();
					String response = "HTTP/1.1 200 OK\n\r\n\r";
					response = response + "Date: " + new Date() + "\n\r\n\r";
					response = response + "Content-Type: text/html\n\r\n\r";
					response = response + "Content-Length: "
							+ resMsg.getBytes().length + "\n\r\n\r";
					response = response + resMsg;

					out.write(response.getBytes());
					out.flush();
					out.close();
				}

				socket.close();
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}
	}

	public LinConnectServer() {
		systemTray();
		run();
	}

	private void run() {
		// System.err.println("Starting");
		try {
			InetAddress intAddr = null;

			Enumeration<NetworkInterface> niEnumeration = NetworkInterface
					.getNetworkInterfaces();
			while (niEnumeration.hasMoreElements() == true) {
				NetworkInterface ni = niEnumeration.nextElement();

				Enumeration<InetAddress> iaEnumeration = ni.getInetAddresses();
				while (iaEnumeration.hasMoreElements() == true) {
					InetAddress addr = iaEnumeration.nextElement();
					if (addr.getHostAddress().startsWith("0:0:0:0:") == false) {
						if (iaEnumeration.hasMoreElements() == true) {
							intAddr = iaEnumeration.nextElement();
							ipAdd = intAddr.getHostAddress();
						}

					}
				}
			}

			this.jmdns = JmDNS.create(intAddr);

			String hostName = InetAddress.getLocalHost().getHostName();
			ServiceInfo serviceInfo = ServiceInfo.create(
					"_linconnect._tcp.local", hostName, PORT, ipAdd + ":9090");

			this.jmdns.registerService(serviceInfo);

			this.serverSocket = new ServerSocket(PORT);

			while (runningServer == true) {
				Socket server = this.serverSocket.accept();
				MultipleSocketServer mss = new MultipleSocketServer(server);
				Thread t = new Thread(mss);
				t.start();
			}

		} catch (IOException e) {
			System.out.println(e.getMessage());
		} finally {
			// System.err.println("Closing");
			try {
				this.serverSocket.close();
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}

			this.jmdns.unregisterAllServices();
			try {
				this.jmdns.close();
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}
	}

	private void systemTray() {
		if (SystemTray.isSupported() == false) {
			return;
		}

		SystemTray tray = SystemTray.getSystemTray();

		URL url = this.getClass().getResource("ic_launcher.png");
		ImageIcon ii = new ImageIcon(url);
		Image icon = ii.getImage();

		trayIcon = new TrayIcon(icon, "LinConnect", null);

		trayIcon.setImageAutoSize(true);

		PopupMenu popup = new PopupMenu();

		MenuItem quitItem = new MenuItem("Quit");
		quitItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				runningServer = false;
				System.exit(0);
			}
		});
		popup.add(quitItem);

		trayIcon.setPopupMenu(popup);

		try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			System.out.println(e.getMessage());
		}
	}

	public static void main(String[] args) {
		new LinConnectServer();
	}
}
