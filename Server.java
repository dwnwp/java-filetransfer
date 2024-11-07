import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Server {
    private static final int PORT = 5000;
    private static final String FILE_DIRECTORY = "YOUR DIRECTORY HERE";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";

    public static void main(String[] args) {
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.bind(new InetSocketAddress(PORT));
            System.out.println(ANSI_GREEN + "Server started." + ANSI_YELLOW + " Waiting for clients..." + ANSI_RESET);

            while (true) {
                SocketChannel clientSocketChannel = serverSocketChannel.accept();
                System.out.println(ANSI_GREEN + "Client connected: " + clientSocketChannel.getLocalAddress() + ANSI_RESET);
                new ClientHandler(clientSocketChannel).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private SocketChannel clientSocketChannel;

        public ClientHandler(SocketChannel socketChannel) {
            this.clientSocketChannel = socketChannel;
        }

        @Override
        public void run() {
            try (
                    OutputStream outputStream = Channels.newOutputStream(clientSocketChannel);
                    InputStream inputStream = Channels.newInputStream(clientSocketChannel);
                    DataOutputStream out = new DataOutputStream(outputStream);
                    DataInputStream in = new DataInputStream(inputStream);) {

                while (true) {
                    long startTime;
                    long endTime = 0;
                    String choice = in.readUTF(); // choice
                    switch (choice) {
                        case "1":
                            boolean validChoice = in.readBoolean(); // validChoice
                            if (validChoice) {
                                String fileName = in.readUTF(); // file.getName()
                                File file = new File(FILE_DIRECTORY + "/" + fileName);
                                if (file.exists()) {
                                    out.writeBoolean(false); // fileNotExist
                                }
                                else {
                                    out.writeBoolean(true); // fileNotExist
                                    long totalFileSize = in.readLong();
                                    int selector = in.readInt(); // selector
                                    // Normal
                                    if (selector == 1) {
                                        normalReadFile(clientSocketChannel, file, totalFileSize);
                                        out.writeUTF("File transfer complete"); // ส่งข้อความยืนยัน confirmationMessage
                                        System.out.println(ANSI_PURPLE + "Client: " + clientSocketChannel.getLocalAddress() + " " + ANSI_CYAN + fileName + ANSI_GREEN + " uploaded successfully. " + ANSI_RESET);
                                    }
                                    // Zero Copy
                                    else if (selector == 2) {
                                        zeroReadFile(clientSocketChannel, file, totalFileSize);
                                        out.writeUTF("File transfer complete"); // ส่งข้อความยืนยัน confirmationMessage
                                        System.out.println(ANSI_PURPLE + "Client: " + clientSocketChannel.getLocalAddress() + " " + ANSI_CYAN + fileName + ANSI_GREEN + " uploaded successfully. " + ANSI_RESET);
                                    }
                                }
                            }
                            break;
                        case "2":
                            // ส่ง list ไฟล์ ไปยัง client
                            File dir = new File(FILE_DIRECTORY);
                            File[] filesList = dir.listFiles();
                            if (filesList != null) {
                                out.writeInt(filesList.length); // fileCount
                                for (File f : filesList) {
                                    out.writeUTF(f.getName()); // filesNameList
                                }
                            }
                            // จบการส่ง list ไฟล์

                            // Wait for client's file choice
                            String fName = in.readUTF(); // filesNameList.get(fIndex)
                            if (fName.equals("none")) { // ถ้า client ใส่ค่าที่ไม่มีมา
                                break;
                            }
                            File file = new File(FILE_DIRECTORY + "/" + fName);
                            if (file.exists()) {
                                out.writeBoolean(true); //fileExists
                                out.writeLong(file.length()); // totalFileSize
                                // Get file extension
                                String fileExtension = fName.substring(fName.lastIndexOf('.') + 1);
                                out.writeUTF(fileExtension); // fileExtension
                                // End
                                int selector = in.readInt(); // selector
                                // Normal
                                if (selector == 1) {
                                    startTime = System.currentTimeMillis();
                                    normalWriteFile(clientSocketChannel, file);
                                    String confirmationMessage = in.readUTF(); // รับข้อความยืนยันจากserver "File transfer complete"
                                    if ("File transfer complete".equals(confirmationMessage)) {
                                        endTime = System.currentTimeMillis();
                                        out.writeLong(endTime-startTime);
                                    }
                                    System.out.println(ANSI_PURPLE + "Client: " + clientSocketChannel.getLocalAddress() + ANSI_YELLOW +" downloaded '" + fName + "' " + ANSI_RESET);
                                }
                                // Zero Copy
                                else if (selector == 2) {
                                    startTime = System.currentTimeMillis();
                                    zeroWriteFile(clientSocketChannel, file);
                                    String confirmationMessage = in.readUTF(); // รับข้อความยืนยันจากserver "File transfer complete"
                                    if ("File transfer complete".equals(confirmationMessage)) {
                                        endTime = System.currentTimeMillis();
                                        out.writeLong(endTime-startTime);
                                    }
                                    System.out.println(ANSI_PURPLE + "Client: " + clientSocketChannel.getLocalAddress() + ANSI_YELLOW +" downloaded '" + fName + "' " + ANSI_RESET);
                                }
                            } else {
                                out.writeBoolean(false);
                            }
                            break;
                        case "3":   //ส่วนที่ 3 disconnect
                            System.out.println(ANSI_RED + "Client Disconnected: " + clientSocketChannel.getLocalAddress() + ANSI_RESET);
                            in.close();
                            out.close();
                            inputStream.close();
                            outputStream.close();
                            clientSocketChannel.close();
                            return; //จบส่วนที่ 3 disconnect
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void normalWriteFile(SocketChannel clientSocketChannel, File file) throws IOException {
        FileChannel fileChannel = FileChannel.open(Paths.get(FILE_DIRECTORY + "/" + file.getName()), StandardOpenOption.READ);
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);
        while (fileChannel.read(buffer) > 0) {
            buffer.flip(); // เตรียมข้อมูลสำหรับการอ่าน
            clientSocketChannel.write(buffer);
            buffer.clear(); // เคลียร์ buffer สำหรับอ่านข้อมูลครั้งถัดไป
        }
        fileChannel.close();
    }
    private static void normalReadFile(SocketChannel socketChannel, File file, long totalFileSize) throws IOException {
        FileChannel fileChannel = FileChannel.open(Paths.get(FILE_DIRECTORY + "/" + file.getName()), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);
        for (long bytesRead = 0; bytesRead < totalFileSize;){
            bytesRead += socketChannel.read(buffer);
            buffer.flip();
            fileChannel.write(buffer);
            buffer.clear();
        }
        fileChannel.close();
    }
    private static void zeroWriteFile(SocketChannel clientSocketChannel, File file) throws IOException {
        FileChannel fileChannel = FileChannel.open(Paths.get(FILE_DIRECTORY + "/" + file.getName()), StandardOpenOption.READ);
        // ใช้ `transferTo` เพื่อส่งข้อมูลจาก FileChannel ไปยัง SocketChannel
        long fileSize = fileChannel.size();
        long position = 0;
        while (position < fileSize) {
            position += fileChannel.transferTo(position, fileSize - position, clientSocketChannel);
        }
        fileChannel.close();
    }
    private static void zeroReadFile(SocketChannel socketChannel, File file, long totalFileSize) throws IOException {
        FileChannel fileChannel = FileChannel.open(Paths.get(FILE_DIRECTORY + "/" + file.getName()), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        // ใช้ `transferFrom` เพื่อรับข้อมูลจาก SocketChannel ไปยัง FileChannel
        for (long bytesRead = 0; bytesRead < totalFileSize;) {
            bytesRead += fileChannel.transferFrom(socketChannel, bytesRead, totalFileSize - bytesRead);
        }
        fileChannel.close();
    }
}
