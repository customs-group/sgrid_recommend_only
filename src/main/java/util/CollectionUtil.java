package util;

import com.google.common.collect.Maps;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * Created by edwardlol on 16/9/19.
 */
public class CollectionUtil {

    //~ Constructors -----------------------------------------------------------

    // Suppress default constructor for noninstantiability
    private CollectionUtil() {
        throw new AssertionError();
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * count the object numbers of a list
     * @param objectList the input list
     * @param <T> the type of objects in the list
     * @return a {@link HashMap} contains all object and their counts
     */
    public static <T> Map<T, Integer> countList(List<T> objectList) {
        Map<T, Integer> resultMap = Maps.newHashMap();
        objectList.forEach(object -> {
            int count = resultMap.containsKey(object) ? resultMap.get(object) + 1 : 1;
            resultMap.put(object, count);
        });
        return resultMap;
    }

    /**
     * update a map consists objects and their counts
     * if the map already contains the object, update its count,
     * otherwise, put(object, 1)
     * @param originalMap the original count map
     * @param object the object to be updated
     * @param <T> the type of the key of the map
     * @return the updated count map
     */
    @SuppressWarnings("unused")
    public static <T> Map<T, Integer> updateCountMap(Map<T, Integer> originalMap, T object) {
        int count = originalMap.containsKey(object) ? originalMap.get(object) + 1 : 1;
        originalMap.put(object, count);
        return originalMap;
    }

    /**
     * sort a map by its values by acsending order
     * @param map the original {@link Map}
     * @param <K> the type of the keys
     * @param <V> the type of the values
     * @return a sorted {@link LinkedHashMap}
     */
    @SuppressWarnings("unused")
    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map ) {
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * sort a map by its values by descending order
     * @param map the original {@link Map}
     * @param <K> the type of the keys
     * @param <V> the type of the values
     * @return a sorted {@link LinkedHashMap}
     */
    @SuppressWarnings("unused")
    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValueDesc(Map<K, V> map ) {
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
}

// End CollectionUtil.java
