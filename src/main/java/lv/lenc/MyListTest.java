package lv.lenc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MyListTest<E> extends ArrayList<E> implements List<E> {

    @Override
    public boolean add(E e) {
        if (this.size() >= 10) {
            throw new IllegalStateException("Нельзя добавить более 10 элементов.");
        }
        return super.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        if (this.size() + c.size() > 10) {
            throw new IllegalStateException("Нельзя добавить более 10 элементов.");
        }
        return super.addAll(c);
    }

    @Override
    public E remove(int index) {
        return super.remove(index);
    }
}
