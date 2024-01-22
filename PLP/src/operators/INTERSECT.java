package operators;

import java.util.LinkedList;

public class INTERSECT<T> implements Operator<T> {

	@Override
	public LinkedList<T> process(LinkedList<T> obj1, LinkedList<T> obj2) {
		LinkedList<T> ret = new LinkedList<T>();
		for (T element : obj2) {
            if (obj1.contains(element)) {
            	ret.add(element);
            }
        }
		return ret;
	}

}
