import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;

public class Client {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final String FILE_DIRECTORY = "YOUR DIRECTORY HERE";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_BOLD = "\u001B[1m";

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        try (SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(SERVER_ADDRESS, SERVER_PORT));
             OutputStream outputStream = Channels.newOutputStream(socketChannel);
             InputStream inputStream = Channels.newInputStream(socketChannel);
             DataOutputStream out = new DataOutputStream(outputStream);
             DataInputStream in = new DataInputStream(inputStream);
        ) {

            System.out.println(ANSI_GREEN + "Connecting to sever..." + ANSI_RESET);

            while (true) {
                long startTime;
                long endTime = 0;
                long totalTime;
                File dir = new File(FILE_DIRECTORY);
                File[] filesList = dir.listFiles();
                // หน้าหลัก
                System.out.println("---------------------------------");
                System.out.println("[1] Upload a file");
                System.out.println("[2] Download a file from server");
                System.out.println("[3] Disconnect");
                System.out.println("---------------------------------");
                System.out.print(ANSI_YELLOW + "Please select number: " + ANSI_RESET);
                String choice = sc.nextLine();
                System.out.println("---------------------------------");
                out.writeUTF(choice);  // choice
                // จบหน้าหลัก
                switch (choice) {
                    case "1":
                        if (filesList == null || filesList.length == 0) {
                            System.out.println(ANSI_RED + ANSI_BOLD + "No files available for upload." + ANSI_RESET);
                            out.writeBoolean(false); // validChoice
                            break;
                        }
                        System.out.println("Files available for upload:");
                        for (int i = 0; i < filesList.length; i++) {
                            System.out.println(ANSI_CYAN + "[" + (i + 1) + "] " + filesList[i].getName() + ANSI_RESET);
                        }
                        System.out.print(ANSI_YELLOW + "Enter the number of the file to upload: " + ANSI_RESET);
                        boolean validChoice = false;
                        int fileIndex = sc.nextInt() - 1;
                        System.out.println("---------------------------------");
                        sc.nextLine(); // Consume the newline
                        if (fileIndex >= 0 && fileIndex < filesList.length) {
                            validChoice = true;
                        }
                        if (validChoice) {
                            out.writeBoolean(validChoice); // validChoice
                            File file = filesList[fileIndex];
                            out.writeUTF(file.getName()); // fileName
                            boolean fileNotExist = in.readBoolean(); // file.exist()
                            // ส่วน upload ละ
                            if (fileNotExist) {
                                out.writeLong(file.length());
                                System.out.print("[1]Normal [2]ZeroCopy: ");
                                int selector = sc.nextInt();
                                sc.nextLine();
                                out.writeInt(selector); // selector
                                if (selector == 1) {
                                    // Normal
                                    System.out.println("Uploading...");
                                    startTime = System.currentTimeMillis();
                                    normalWriteFile(socketChannel, file);
                                    String confirmationMessage = in.readUTF(); // รับข้อความยืนยันจากserver "File transfer complete"
                                    if ("File transfer complete".equals(confirmationMessage)) {
                                        endTime = System.currentTimeMillis();
                                    }
                                    System.out.println("File uploaded. " + String.format("%.2f" ,(float)(endTime-startTime)/(float)1000) + " seconds");
                                } else if (selector == 2) {
                                    // Zero Copy
                                    System.out.println("Uploading...");
                                    startTime = System.currentTimeMillis();
                                    zeroWriteFile(socketChannel, file);
                                    String confirmationMessage = in.readUTF(); // รับข้อความยืนยันจากserver "File transfer complete"
                                    if ("File transfer complete".equals(confirmationMessage)) {
                                        endTime = System.currentTimeMillis();
                                    }
                                    System.out.println("File uploaded. " + String.format("%.2f" ,(float)(endTime-startTime)/(float)1000) + " seconds");
                                }
                            } else {
                                System.out.println(ANSI_RED + ANSI_BOLD + "File already exists on the server." + ANSI_RESET);
                            }
                        } else {
                            out.writeBoolean(validChoice);
                            System.out.println(ANSI_RED + ANSI_BOLD + "Invalid choice." + ANSI_RESET);
                        }
                        break;
                    case "2":
                        // รับ list ของไฟล์มาจาก server
                        int fileCount = in.readInt();
                        List<String> filesNameList = new ArrayList<>();
                        for (int i = 0; i < fileCount; i++) {
                            filesNameList.add(in.readUTF()); // file.getName()
                        }
                        if (fileCount == 0) {
                            System.out.println(ANSI_RED + ANSI_BOLD + "No files available for download." + ANSI_RESET);
                            out.writeUTF("none"); // fName
                            break;
                        }
                        System.out.println("Files available for download:");
                        for (int i = 0; i < filesNameList.size(); i++) { // แสดงผล
                            System.out.println(ANSI_CYAN+"[" + (i + 1) + "] " + filesNameList.get(i)+ANSI_RESET);
                        }
                        // จบการรับ list

                        // เลือกหมายเลขที่อ้างอิงถึงชื่อไฟล์ ที่จะ download
                        System.out.print(ANSI_YELLOW + "Enter the number of the file to download: " + ANSI_RESET);
                        int fIndex = sc.nextInt() - 1;
                        System.out.println("---------------------------------");
                        sc.nextLine();
                        boolean check = true;
                        if (fIndex >= 0 && fIndex < filesNameList.size()) {
                            out.writeUTF(filesNameList.get(fIndex)); // ส่งเอาชื่อไฟล์ไปยัง server เพื่อค้นหาไฟล์ fName
                            boolean fileExists = in.readBoolean(); // ถ้าเจอ server จะ return true
                            if (fileExists) {
                                File file;
                                long totalFileSize = in.readLong(); // file.length()
                                String fileExtension = in.readUTF(); // fileExtension
                                System.out.print("[1]Normal [2]ZeroCopy: ");
                                int selector = sc.nextInt();
                                sc.nextLine();
                                out.writeInt(selector);
                                System.out.print("Name a file: ");
                                String fileName = sc.nextLine();
                                // ไม่ใส่ extension มา
                                if (fileName.indexOf('.') == -1){
                                    file = new File(FILE_DIRECTORY + "/" + fileName + "." + fileExtension);
                                }
                                // ใส่ extension มา
                                else {
                                    if (fileName.substring(fileName.lastIndexOf('.') + 1).equals(fileExtension)){
                                        file = new File(FILE_DIRECTORY + "/" + fileName);
                                    }else {
                                        file = new File(FILE_DIRECTORY + "/" + fileName.substring(0,fileName.lastIndexOf('.')) + "." + fileExtension);
                                    }
                                }
                                if (selector == 1) {
                                    // Normal
                                    System.out.println("Downloading...");
                                    normalReadFile(socketChannel, file, totalFileSize);
                                    out.writeUTF("File transfer complete"); // ส่งข้อความยืนยัน confirmationMessage
                                    totalTime = in.readLong();
                                    System.out.println("File downloaded. " + String.format("%.2f" ,(float)(totalTime)/(float)1000) + " seconds");
                                } else if (selector == 2) {
                                    // Zero Copy
                                    System.out.println("Downloading...");
                                    zeroReadFile(socketChannel, file, totalFileSize);
                                    out.writeUTF("File transfer complete"); // ส่งข้อความยืนยัน confirmationMessage
                                    totalTime = in.readLong();
                                    System.out.println("File downloaded. " + String.format("%.2f" ,(float)(totalTime)/(float)1000) + " seconds");
                                }
                            } else {
                                System.out.println(ANSI_RED + ANSI_BOLD + "File does not exist on the server." + ANSI_RESET);
                            }
                        } else {
                            out.writeUTF("none");
                            System.out.println(ANSI_RED + ANSI_BOLD + "Invalid file choice!" + ANSI_RESET);
                        }
                        // จบส่วนเลือกหมายเลขที่จะ download
                        break;
                    case "3": // ส่วนที่ 3 disconnect
                        System.out.println(ANSI_RED + ANSI_BOLD + "Disconnected!" + ANSI_RESET);
                        return; // จบส่วนที่ 3 disconnect
                    default:
                        System.out.println(ANSI_RED + ANSI_BOLD + "Invalid Choice!!" + ANSI_RESET);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void normalReadFile(SocketChannel socketChannel, File file, long totalFileSize) throws IOException {
        FileChannel fileChannel = FileChannel.open(Paths.get(FILE_DIRECTORY + "/" + file.getName()),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);
        for (long bytesRead = 0; bytesRead < totalFileSize; ) {
            bytesRead += socketChannel.read(buffer);
            buffer.flip();
            fileChannel.write(buffer);
            buffer.clear();
        }
        fileChannel.close();
    }
    private static void normalWriteFile(SocketChannel socketChannel, File file) throws IOException {
        FileChannel fileChannel = FileChannel.open(Paths.get(FILE_DIRECTORY + "/" + file.getName()), StandardOpenOption.READ);
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);
        while (fileChannel.read(buffer) > 0) {
            buffer.flip(); // เตรียมข้อมูลสำหรับการอ่าน
            socketChannel.write(buffer);
            buffer.clear(); // เคลียร์ buffer สำหรับอ่านข้อมูลครั้งถัดไป
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
    private static void zeroWriteFile(SocketChannel socketChannel, File file) throws IOException {
        FileChannel fileChannel = FileChannel.open(Paths.get(FILE_DIRECTORY + "/" + file.getName()), StandardOpenOption.READ);
        // ใช้ `transferTo` เพื่อส่งข้อมูลจาก FileChannel ไปยัง SocketChannel
        long fileSize = fileChannel.size();
        long position = 0;
        while (position < fileSize) {
            position += fileChannel.transferTo(position, fileSize - position, socketChannel);
        }
        fileChannel.close();
    }
}
