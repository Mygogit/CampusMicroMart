import java.nio.file.*;
public class TestPath {
    public static void main(String[] args) throws Exception {
        Path p = Paths.get(args[0]);
        System.out.println("Path: " + p);
        System.out.println("Exists: " + Files.exists(p));
    }
}
