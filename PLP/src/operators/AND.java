package operators;

import java.util.LinkedList;

public class AND<T> implements Operator<T> {

	@Override
	public LinkedList<T> process(LinkedList<T> obj1, LinkedList<T> obj2) {
		LinkedList<T> ret = new LinkedList<T>();
		ret.addAll(obj1);
		for (T element : obj2) {
            if (!ret.contains(element)) {
            	ret.add(element);
            }
        }
		return ret;
	}

}
