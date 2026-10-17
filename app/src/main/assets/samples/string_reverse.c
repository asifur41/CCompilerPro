#include <stdio.h>
#include <string.h>

void reverse(char *s) {
    int i = 0;
    int j = (int)strlen(s) - 1;
    while (i < j) {
        char tmp = s[i];
        s[i] = s[j];
        s[j] = tmp;
        i++;
        j--;
    }
}

int main() {
    char text[] = "CCompiler Pro runs offline!";
    printf("Original: %s\n", text);
    reverse(text);
    printf("Reversed: %s\n", text);
    return 0;
}
