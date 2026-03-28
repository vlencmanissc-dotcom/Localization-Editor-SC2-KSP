package lv.lenc;

import java.util.ArrayList;
import java.util.Collection;

public class MyListTest<E> extends ArrayList<E> {

    @Override
    public boolean add(E e) {
        if (this.size() >= 10) {
            throw new IllegalStateException("Cannot add more than 10 elements.");
        }
        return super.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        if (this.size() + c.size() > 10) {
            throw new IllegalStateException("Cannot add more than 10 elements.");
        }
        return super.addAll(c);
    }

    @Override
    public E remove(int index) {
        return super.remove(index);
    }
}
