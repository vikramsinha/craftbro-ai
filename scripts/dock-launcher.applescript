on run
    set appPath to POSIX path of (path to me)
    set projectPath to do shell script "/usr/bin/dirname " & quoted form of appPath
    do shell script "/usr/bin/open -a Terminal " & quoted form of (projectPath & "/Launch Minecraft with Telemetry.command")
end run
