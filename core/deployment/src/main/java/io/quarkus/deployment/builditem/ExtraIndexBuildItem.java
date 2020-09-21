package io.quarkus.deployment.builditem;

import org.jboss.jandex.IndexView;

import io.quarkus.builder.item.SimpleBuildItem;

public final class ExtraIndexBuildItem extends SimpleBuildItem {

    private final IndexView index;

    public ExtraIndexBuildItem(IndexView index) {
        this.index = index;
    }

    public IndexView getIndex() {
        return index;
    }
}
