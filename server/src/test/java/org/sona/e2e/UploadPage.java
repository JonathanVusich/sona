package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.SelectOption;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

/**
 * The admin page that uploads music: files are picked, sorted into groups, then uploaded.
 */
@RequiredArgsConstructor
public final class UploadPage {

    private final Page page;

    public void open() {
        page.navigate("/admin/upload");
    }

    public void chooseFiles(final FilePayload... files) {
        final var chooser = page.waitForFileChooser(() -> button("Choose files").click());
        chooser.setFiles(files);
    }

    public void chooseFolder(final Path folder) {
        final var chooser =
                page.waitForFileChooser(() -> button("Choose folder").click());
        chooser.setFiles(folder);
    }

    public Locator group(final String name) {
        return page.getByRole(
                AriaRole.REGION, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator groupFiles(final String name) {
        return group(name).locator(".pending-path");
    }

    public void renameGroup(final String name, final String newName) {
        group(name).getByLabel("Group name").fill(newName);
    }

    public void moveFile(final String path, final String groupName) {
        page.getByLabel("Move " + path + " to").selectOption(new SelectOption().setLabel(groupName));
    }

    public void moveAllFiles(final String fromGroup, final String toGroup) {
        page.getByLabel("Move all files in " + fromGroup + " to").selectOption(new SelectOption().setLabel(toGroup));
    }

    public Locator uploadButton() {
        return button("Upload");
    }

    public Locator status(final String path) {
        return page.getByRole(AriaRole.LISTITEM)
                .filter(new Locator.FilterOptions().setHasText(path))
                .locator(".upload-status");
    }

    public Locator summary() {
        return page.locator(".upload-summary");
    }

    public Locator ingestSummary() {
        return page.locator(".ingest-summary");
    }

    public Locator progressBar(final String path) {
        return page.getByRole(
                AriaRole.PROGRESSBAR,
                new Page.GetByRoleOptions().setName("Uploading " + path).setExact(true));
    }

    public Locator retryButton(final String path) {
        return button("Retry " + path);
    }

    public Locator error() {
        return page.locator(".error");
    }

    private Locator button(final String name) {
        return page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }
}
