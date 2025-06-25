package nlScript.mic;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ResizableUndecoratedFrame {

	private static final int GRIP_SIZE = 16;  // size of the resize grip

	public static void makeUndecoratedResizable(JDialog window) {
		window.setUndecorated(true);
		// Use a JLayeredPane so we can stack the grip on top
		JLayeredPane layered = window.getLayeredPane();

		// The resize grip
		ResizeGrip grip = new ResizeGrip(window);
		grip.setBounds(
				window.getWidth() - GRIP_SIZE,
				window.getHeight() - GRIP_SIZE,
				GRIP_SIZE, GRIP_SIZE);
		layered.add(grip, JLayeredPane.PALETTE_LAYER);

		// Keep grip positioned on resize
		layered.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				Dimension size = window.getSize();
				grip.setLocation(size.width - GRIP_SIZE, size.height - GRIP_SIZE);
			}
		});
	}

	private static class ResizeGrip extends JComponent {

		private final Window window;

		private Point dragStart;

		ResizeGrip(Window w) {
			this.window = w;
			setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));

			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					dragStart = SwingUtilities.convertPoint(ResizeGrip.this, e.getPoint(), getRootPane());
				}
				@Override
				public void mouseReleased(MouseEvent e) {
					dragStart = null;
				}
			});

			addMouseMotionListener(new MouseMotionAdapter() {
				@Override
				public void mouseDragged(MouseEvent e) {
					if (dragStart != null) {
						Point pt = SwingUtilities.convertPoint(ResizeGrip.this, e.getPoint(), getRootPane());
						int dx = pt.x - dragStart.x;
						int dy = pt.y - dragStart.y;
						Dimension sz = getRootPane().getSize();
						int newWidth = sz.width + dx;
						int newHeight = sz.height + dy;
						window.setSize(Math.max(newWidth, 0), Math.max(newHeight, 0));

						if(newHeight > 0) dragStart.y = pt.y;
						if(newWidth  > 0) dragStart.x = pt.x;
					}
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g) {
			// draw a simple diagonal “grip” of dots
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setColor(Color.DARK_GRAY);
			for (int i = 4; i < GRIP_SIZE; i += 4) {
				for (int j = 4; j < GRIP_SIZE; j += 4) {
					g2.fillRect(i, j, 2, 2);
				}
			}
			g2.dispose();
		}
	}

	public static void main(String[] args) {
		JDialog window = new JDialog();
		window.setSize(400, 300);
		window.setLocationRelativeTo(null);
		window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		// Main content panel
		JPanel content = new JPanel();
		content.setBackground(Color.ORANGE);
		window.getContentPane().add(content);

		makeUndecoratedResizable(window);

		SwingUtilities.invokeLater(() -> window.setVisible(true));
	}
}
