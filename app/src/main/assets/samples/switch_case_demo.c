#include <stdio.h>

const char *day_name(int d) {
    switch (d) {
        case 1: return "Monday";
        case 2: return "Tuesday";
        case 3: return "Wednesday";
        case 4: return "Thursday";
        case 5: return "Friday";
        case 6: return "Saturday";
        case 7: return "Sunday";
        default: return "Invalid day";
    }
}

int is_vowel(char c) {
    switch (c) {
        case 'a': case 'e': case 'i': case 'o': case 'u':
        case 'A': case 'E': case 'I': case 'O': case 'U':
            return 1;
        default:
            return 0;
    }
}

int main() {
    int d;
    const char *word = "Programming";
    int vowels = 0;
    int i;

    for (d = 1; d <= 8; d++) {
        printf("Day %d: %s\n", d, day_name(d));
    }

    for (i = 0; word[i] != '\0'; i++) {
        vowels += is_vowel(word[i]);
    }
    printf("\"%s\" has %d vowels\n", word, vowels);
    return 0;
}
