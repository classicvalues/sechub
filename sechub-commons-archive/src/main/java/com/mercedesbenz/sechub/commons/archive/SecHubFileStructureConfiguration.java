package com.mercedesbenz.sechub.commons.archive;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class SecHubFileStructureConfiguration {

    private Set<String> acceptedReferenceNames = new LinkedHashSet<>();

    private boolean rootFolderAccepted;

    public void setRootFolderAccepted(boolean rootFolderAccepted) {
        this.rootFolderAccepted = rootFolderAccepted;
    }

    public void addAcceptedReferenceNames(Collection<String> uniqueNames) {
        if (uniqueNames == null) {
            return;
        }
        this.acceptedReferenceNames.addAll(uniqueNames);
    }

    public boolean isRootFolderAccepted() {
        return rootFolderAccepted;
    }

    Set<String> getAcceptedReferenceNames() {
        return Collections.unmodifiableSet(acceptedReferenceNames);
    }
}
