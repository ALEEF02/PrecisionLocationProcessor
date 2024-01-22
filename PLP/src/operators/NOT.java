package operators;

import java.util.LinkedList;

public class NOT<T> implements Operator<T> {

	@Override
	/**
	 * Obj1 with any overlap from Obj2 removed
	 * @param obj1
	 * @param obj2
	 * @return The operator result
	 */
	public LinkedList<T> process(LinkedList<T> obj1, LinkedList<T> obj2) {
		LinkedList<T> ret = new LinkedList<T>();
		ret.addAll(obj1);
		for (T element : obj2) {
            if (ret.contains(element)) {
            	ret.remove(element);
            }
        }
		return ret;
	}

}
