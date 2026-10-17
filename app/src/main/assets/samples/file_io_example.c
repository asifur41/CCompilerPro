#include <stdio.h>
#include <stdlib.h>

/* Files are created in the app's private working directory. */
int main() {
    const char *path = "sample_output.txt";
    char line[128];
    int i;
    FILE *fp = fopen(path, "w");

    if (fp == NULL) {
        printf("Could not open %s for writing\n", path);
        return 1;
    }
    for (i = 1; i <= 3; i++) {
        fprintf(fp, "Line %d written from C\n", i);
    }
    fclose(fp);

    fp = fopen(path, "r");
    if (fp == NULL) {
        printf("Could not open %s for reading\n", path);
        return 1;
    }
    printf("Contents of %s:\n", path);
    while (fgets(line, sizeof(line), fp) != NULL) {
        printf("  %s", line);
    }
    fclose(fp);

    remove(path);
    return 0;
}
