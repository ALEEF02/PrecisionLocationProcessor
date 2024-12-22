package plp.operator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import plp.location.LocationCell;

public class OperatorFactory {
    public static List<LocationCell> applyAnd(List<LocationCell> list1, List<LocationCell> list2) {
        return list1.stream().filter(list2::contains).toList();
    }

    public static List<LocationCell> applyOr(List<LocationCell> list1, List<LocationCell> list2) {
        Set<LocationCell> resultSet = new HashSet<>(list1);
        resultSet.addAll(list2);
        return new ArrayList<>(resultSet);
    }

    public static List<LocationCell> applyIntersect(List<LocationCell> list1, List<LocationCell> list2) {
        return applyAnd(list1, list2);
    }
    
    public static List<LocationCell> applyNot(List<LocationCell> list1, List<LocationCell> list2) {
        return list1.stream()
                .filter(cell -> !list2.contains(cell))
                .toList();
    }
    
    public static List<LocationCell> applyExclusiveOr(List<LocationCell> list1, List<LocationCell> list2) {
        Set<LocationCell> resultSet = new HashSet<>(list1);
        resultSet.addAll(list2);

        Set<LocationCell> intersection = new HashSet<>(list1);
        intersection.retainAll(list2);

        resultSet.removeAll(intersection);

        return new ArrayList<>(resultSet);
    }
}
