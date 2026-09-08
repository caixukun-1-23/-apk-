package com.acooldog.toolbox.route.data;

import com.acooldog.toolbox.route.domain.model.RouteDefinition;
import com.acooldog.toolbox.route.domain.model.RoutePoint;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FileRouteRepositoryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private FileRouteRepository repository;
    private File directory;
    private final List<RoutePoint> points = Arrays.asList(
            new RoutePoint(116.4, 39.9, 116.39, 39.89, 50),
            new RoutePoint(116.41, 39.91, 116.40, 39.90, 51));

    @Before public void setUp() throws IOException {
        directory = temporary.newFolder("routes");
        repository = new FileRouteRepository(directory);
    }

    @Test public void duplicateNamesPreserveBothRoutes() throws Exception {
        RouteDefinition first = repository.saveRoute("晨跑", points);
        byte[] original = Files.readAllBytes(first.getFile().toPath());
        RouteDefinition second = repository.saveRoute("晨跑", points);
        assertNotEquals(first.getId(), second.getId());
        assertArrayEquals(original, Files.readAllBytes(first.getFile().toPath()));
        assertEquals(2, repository.getRoutes().size());
    }

    @Test public void sanitizedNameCollisionsDoNotOverwrite() throws Exception {
        RouteDefinition first = repository.saveRoute("a/b", points);
        RouteDefinition second = repository.saveRoute("a:b", points);
        assertNotEquals(first.getId(), second.getId());
        assertEquals("a/b", repository.getRoute(first.getId()).getName());
    }

    @Test public void renamePreservesIdCreationTimeAndOtherRoutes() throws Exception {
        RouteDefinition first = repository.saveRoute("one", points);
        RouteDefinition other = repository.saveRoute("two", points);
        byte[] otherBytes = Files.readAllBytes(other.getFile().toPath());
        RouteDefinition renamed = repository.updateRoute(first.getId(), "two", points, null);
        assertEquals(first.getId(), renamed.getId());
        assertEquals(first.getCreatedAt(), renamed.getCreatedAt());
        assertEquals("two", repository.getRoute(first.getId()).getName());
        assertArrayEquals(otherBytes, Files.readAllBytes(other.getFile().toPath()));
        assertEquals(2, repository.getRoutes().size());
    }

    @Test public void repeatedImportPreservesBothCopies() throws Exception {
        RouteDefinition source = repository.saveRoute("source", points);
        byte[] json = Files.readAllBytes(source.getFile().toPath());
        RouteDefinition first = repository.importRoute("shared.json", new ByteArrayInputStream(json));
        RouteDefinition second = repository.importRoute("shared.json", new ByteArrayInputStream(json));
        assertNotEquals(first.getId(), second.getId());
        assertEquals(3, repository.getRoutes().size());
    }

    @Test public void malformedImportDoesNotChangeExistingFile() throws Exception {
        RouteDefinition existing = repository.saveRoute("shared", points);
        byte[] original = Files.readAllBytes(existing.getFile().toPath());
        assertThrows(IOException.class, () -> repository.importRoute("shared", input("{bad json")));
        assertArrayEquals(original, Files.readAllBytes(existing.getFile().toPath()));
        assertEquals(1, directory.list().length);
    }

    @Test public void importRejectsEmptyOrInvalidCoordinates() {
        assertThrows(IOException.class, () -> repository.importRoute("empty", input("[]")));
        String invalid = "{\"bdLongitude\":116,\"bdLatitude\":91,\"wgsLongitude\":116,\"wgsLatitude\":39}";
        assertThrows(IOException.class, () -> repository.importRoute("invalid", input("[" + invalid + "," + invalid + "]")));
        String nonFinite = "{\"bdLongitude\":\"NaN\",\"bdLatitude\":39,\"wgsLongitude\":116,\"wgsLatitude\":39}";
        assertThrows(IOException.class, () -> repository.importRoute("nan", input("[" + nonFinite + "," + nonFinite + "]")));
        assertEquals(0, directory.list().length);
    }

    @Test public void oversizedImportStopsWithoutWriting() {
        byte[] bytes = new byte[FileRouteRepository.MAX_ROUTE_BYTES + 1];
        assertThrows(IOException.class, () -> repository.importRoute("huge", new ByteArrayInputStream(bytes)));
        assertEquals(0, directory.list().length);
    }

    @Test public void traversalCannotReadUpdateOrDeleteOtherFiles() throws Exception {
        File outside = temporary.newFile("outside.json");
        Files.write(outside.toPath(), "private".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> repository.getRoute("../outside.json"));
        assertThrows(IOException.class, () -> repository.deleteRoute("../outside.json"));
        assertThrows(IOException.class, () -> repository.updateRoute("../outside.json", "x", points, null));
        assertTrue(outside.exists());
    }

    @Test public void symlinkCannotEscapeRouteDirectory() throws Exception {
        File outside = temporary.newFile("outside.json");
        Files.createSymbolicLink(new File(directory, "link.json").toPath(), outside.toPath());
        assertThrows(IOException.class, () -> repository.getRoute("link.json"));
        assertThrows(IOException.class, () -> repository.deleteRoute("link.json"));
        assertTrue(outside.exists());
    }

    @Test public void failedReplacementPreservesOriginalAndCleansTemporaryFiles() throws Exception {
        RouteDefinition existing = repository.saveRoute("safe", points);
        byte[] original = Files.readAllBytes(existing.getFile().toPath());
        char[] characters = new char[FileRouteRepository.MAX_ROUTE_BYTES];
        Arrays.fill(characters, 'x');
        assertThrows(IOException.class, () -> repository.updateRoute(existing.getId(), new String(characters), points, null));
        assertArrayEquals(original, Files.readAllBytes(existing.getFile().toPath()));
        assertEquals(1, directory.list().length);
    }

    @Test public void missingUpdateDoesNotRecreateDeletedRoute() {
        assertThrows(IOException.class, () -> repository.updateRoute("missing.json", "x", points, null));
        assertEquals(0, directory.list().length);
    }

    @Test public void longUnicodeNamesRemainWritable() throws Exception {
        String name = String.join("", java.util.Collections.nCopies(200, "路线"));
        RouteDefinition route = repository.saveRoute(name, points);
        assertEquals(name, repository.getRoute(route.getId()).getName());
        assertTrue(route.getFile().getName().getBytes(StandardCharsets.UTF_8).length < 255);
    }

    private ByteArrayInputStream input(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
