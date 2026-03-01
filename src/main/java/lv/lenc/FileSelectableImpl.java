package lv.lenc;

import java.io.File;

public class FileSelectableImpl implements FileSelectable {
    GlowingLabelWithBorder fileTitleLabel;

    public FileSelectableImpl(GlowingLabelWithBorder fileTitleLabel) {
        this.fileTitleLabel = fileTitleLabel;
    }

    public void onSelect(File file) {
        fileTitleLabel.setText(file.getName());
    }
}
