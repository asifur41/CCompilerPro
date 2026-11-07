/*
 * Title: Count Vowels and Consonants
 * Goes through a string and counts vowels, consonants and other characters with the ctype functions.
 */
#include <stdio.h>
#include <ctype.h>

int main() {
    const char *text = "Programming in C is fun!";
    int vowels = 0;
    int consonants = 0;
    int others = 0;
    int i;

    for (i = 0; text[i] != '\0'; i++) {
        char c = (char)tolower((unsigned char)text[i]);
        if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') {
            vowels++;
        } else if (isalpha((unsigned char)c)) {
            consonants++;
        } else {
            others++;
        }
    }

    printf("Text: %s\n", text);
    printf("Vowels: %d\n", vowels);
    printf("Consonants: %d\n", consonants);
    printf("Others (spaces, symbols): %d\n", others);
    return 0;
}
