package iq.haji.alhjexx.model;

public class Category {
    public long id;
    public String name;
    public int sortOrder;
    public boolean active = true;
    public boolean archived;

    @Override
    public String toString() {
        return name;
    }
}
