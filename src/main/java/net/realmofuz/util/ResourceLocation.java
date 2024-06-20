package net.realmofuz.util;

import java.util.ArrayList;
import java.util.List;

public record ResourceLocation(
    List<String> names
) {
    public ResourceLocation(String str) {
        this(List.of(str.split("[:/]")));
    }

    @Override
    public String toString() {
        var al = new ArrayList<>(this.names);
        var namespace = al.removeFirst();
        var rest = String.join("/", al);
        return namespace + ":" + rest;
    }

    public String getNamespace() {
        var al = new ArrayList<>(this.names);
        return al.removeFirst();
    }

    public String getPath() {
        var al = new ArrayList<>(this.names);
        al.removeFirst();
        return String.join("/", al);
    }

    public String toFilePath() {
        return String.join("/", this.names);
    }

    public ResourceLocation withoutSuffix() {
        var al = new ArrayList<>(this.names);
        al.removeLast();
        return new ResourceLocation(al);
    }

    public ResourceLocation withSuffix(String suffix) {
        var al = new ArrayList<>(this.names);
        al.add(suffix);
        return new ResourceLocation(al);
    }

    public ResourceLocation prependPath(String prepend) {
        var al = new ArrayList<>(this.names);
        al.add(1, prepend);
        return new ResourceLocation(al);
    }
}
