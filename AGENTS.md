## Android app requirements

- This is an Android application with one launcher `MainActivity`.
- The interface must be entirely in English and use Material 3.
- Follow the system light/dark theme automatically.
- The main screen currently displays `Hello`.
- The top bar has a monochrome settings icon on the left, represented by the classic icon of engrenage
the title `Hello`, and a monochrome today icon aligned on the right. The today
  icon is displayed but has no behavior yet.
- The settings screen is shown over the main screen and currently contains:
  - a first section selecting the Android calendar to use. if it is not available yet becasue of permission, then a button to ask for permission is displayed instead and then when done, the button to pick the calendar is displayed in place of the one that ask permission
  
  - a final centered horizontally, smaller section showing `SimplerCal v<release version>`
    and the url of the project on github, displayed as a html link. if the current version is not an official github release, then <release version> is "---"
- Keep the implementation ready for a future calendar week view, but do not
  add that view until its visual design is specified.
