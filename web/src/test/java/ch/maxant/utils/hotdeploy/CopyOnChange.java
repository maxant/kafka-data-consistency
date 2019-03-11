package ch.maxant.utils.hotdeploy;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.StandardWatchEventKinds.*;

public class CopyOnChange {

    public static void main(String[] args) throws IOException, InterruptedException {
        WatchService watchService = FileSystems.getDefault().newWatchService();
        Path root = Paths.get("./web/src/main/webapp");
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                System.out.println("Watching " + dir.toFile().getAbsolutePath());
                dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });

        WatchKey key;
        while ((key = watchService.take()) != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                Path p = (Path) event.context();
                WatchEvent.Kind<Path> kind = (WatchEvent.Kind<Path>) event.kind();
                if(kind == ENTRY_CREATE && p.toFile().isDirectory()) {
                    p.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                } else if(kind == ENTRY_DELETE && p.toFile().isDirectory()) {
                    // p.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                }
                System.out.println(
                        "Event kind:" + kind
                                + ". File affected: " + event.context() + "." + event.context().getClass());

                //TODO delete everything except META-INF and WEB-INF

                Path target = Paths.get("./web/target/web");
                Files.copy(root, target,LinkOption.NOFOLLOW_LINKS,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            key.reset();
        }
    }
}
