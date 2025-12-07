package ru.presentation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.function.Consumer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class DualPanelWindow extends JFrame {
    private static final Logger logger = LogManager.getLogger(DualPanelWindow.class);

    private JTextArea writeArea;
    private JTextArea readArea;
    private PipedOutputStream pipedOut;
    private PipedInputStream pipedIn;

    public DualPanelWindow() throws IOException {
        super("Dual Panel Window");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);

        writeArea = new JTextArea();
        writeArea.setEditable(false);
        JScrollPane writeScroll = new JScrollPane(writeArea);

        readArea = new JTextArea();
        JScrollPane readScroll = new JScrollPane(readArea);

        JButton sendButton = new JButton("Отправить");
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendText();
            }
        });

        JPanel lowerPanel = new JPanel(new BorderLayout());
        lowerPanel.add(readScroll, BorderLayout.CENTER);
        lowerPanel.add(sendButton, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, writeScroll, lowerPanel);
        splitPane.setDividerLocation(200);

        getContentPane().add(splitPane, BorderLayout.CENTER);

        pipedOut = new PipedOutputStream();
        pipedIn = new PipedInputStream(pipedOut);
        logger.debug("Окно DualPanelWindow инициализировано");
    }

    private void sendText() {
        String text = readArea.getText();
        if (!text.isEmpty()) {
            try {
                pipedOut.write((text + "\n").getBytes());
                pipedOut.flush();
                readArea.setText("");
                logger.debug("Текст отправлен через PipedOutputStream: {}", text);
            } catch (IOException ex) {
                logger.error("Ошибка при отправке текста через PipedOutputStream", ex);
            }
        }
    }

    public Consumer<String> getWriteConsumer() {
        logger.debug("Создан Consumer для записи в текстовую область");
        return s -> {
            SwingUtilities.invokeLater(() -> {
                writeArea.append(s + "\n");
                writeArea.setCaretPosition(writeArea.getDocument().getLength());
            });
        };
    }

    public InputStreamReader getReadInputStreamReader() {
        logger.debug("Создан InputStreamReader для PipedInputStream");
        return new InputStreamReader(pipedIn);
    }

    public static void drawInterface() throws IOException {
        SwingUtilities.invokeLater(() -> {
            try {
                DualPanelWindow window = new DualPanelWindow();
                window.setVisible(true);
                logger.info("Окно DualPanelWindow отображено");

                Consumer<String> writer = window.getWriteConsumer();
                writer.accept("Пример записи в верхнюю область");
                logger.debug("Отправлен пример текста в верхнюю область");

                InputStreamReader reader = window.getReadInputStreamReader();
                BufferedReader br = new BufferedReader(reader);
                new Thread(() -> {
                    try {
                        String line;
                        while ((line = br.readLine()) != null) {
                            logger.info("Получено из нижней части: {}", line);
                        }
                    } catch (IOException e) {
                        logger.error("Ошибка при чтении из BufferedReader", e);
                    }
                }).start();
                logger.debug("Запущен поток для чтения из нижней части");

            } catch (IOException e) {
                logger.error("Ошибка при создании окна DualPanelWindow", e);
            }
        });
    }
}