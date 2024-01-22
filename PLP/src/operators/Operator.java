package operators;

import java.util.LinkedList;

public interface Operator<T> {
	
	/**
	 * Evaluate the operator on the two LinkedLists
	 * @param obj1
	 * @param obj2
	 * @return The operator result
	 */
	public LinkedList<T> process(LinkedList<T> obj1, LinkedList<T> obj2);
	
}
