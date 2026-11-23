tell application "Finder"
  tell disk "DEPLOY_APP_NAME"
    open
    set current view of container window to icon view
    set toolbar visible of container window to false
    set statusbar visible of container window to false
    set the bounds of container window to {200, 120, 860, 560}
    set theViewOptions to the icon view options of container window
    set arrangement of theViewOptions to not arranged
    set icon size of theViewOptions to 110
    set text size of theViewOptions to 12
    set background picture of theViewOptions to file ".background:DEPLOY_BG_FILE"
    try
      set position of item "Install Focus.app" of container window to {130, 220}
    end try
    try
      set position of item "DEPLOY_APP_NAME.app" of container window to {330, 220}
    end try
    try
      set position of item "Applications" of container window to {530, 220}
    end try
    close
    open
    update without registering applications
    delay 2
  end tell
end tell
