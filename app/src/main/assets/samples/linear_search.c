/*
 * Title: Linear Search
 * Looks at each element in turn until the wanted value is found, or reports that it is missing.
 */
#include <stdio.h>

int linear_search(int arr[], int n, int target) {
    int i;

    for (i = 0; i < n; i++) {
        if (arr[i] == target) {
            return i;
        }
    }
    return -1;
}

int main() {
    int data[] = {14, 8, 42, 7, 23, 91, 5};
    int n = sizeof(data) / sizeof(data[0]);
    int targets[] = {42, 99};
    int t;

    for (t = 0; t < 2; t++) {
        int index = linear_search(data, n, targets[t]);
        if (index >= 0) {
            printf("%d found at index %d\n", targets[t], index);
        } else {
            printf("%d not found\n", targets[t]);
        }
    }
    return 0;
}
