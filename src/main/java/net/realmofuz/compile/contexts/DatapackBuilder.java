package net.realmofuz.compile.contexts;

/**
 * Akin to a StringBuilder, but for modifying Datapacks.
 */
public class DatapackBuilder {
    StringBuffer inner;

    public DatapackBuilder(String str) {
        inner = new StringBuffer(str);
    }

    public DatapackBuilder() {
        this("");
    }

    public DatapackBuilder append(char ch) {
        inner.append(ch);
        return this;
    }

    public DatapackBuilder appendInteger(double number) {
        inner.append((int) number);
        return this;
    }

    public DatapackBuilder append(String str) {
        inner.append(str);
        return this;
    }

    public DatapackBuilder append(Object o) {
        inner.append(o);
        return this;
    }

    @Override
    public String toString() {
        return this.inner.toString();
    }
}
