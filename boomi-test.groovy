class BoomiGroovyTest {
    static void main(String[] args) throws Exception {
        def cli = new CliBuilder(usage: 'boomi-groovy-test.groovy [-h] -f file -s script [-d document]')

        cli.with {
            h  longOpt: 'help', 'Show usage'
            f  longOpt: 'file', args: 1, argName: 'testFile', 'test file'
            xd longOpt: 'suppress-data-output', type: boolean, 'Suppresses output of data'
            xp longOpt: 'suppress-props-output', type: boolean, 'Suppresses output of props'
            w  longOpt: 'working-dir', args: 1, argName: 'dir', 'Present Working Directory'
        }

        def options = cli.parse(args)

        if (options.h || !options.f || !options) {
            cli.usage()
            return
        }

        String opt_xd = options.xd ? "-xd" : ""
        String opt_xp = options.xp ? "-xp" : ""
        String opt_w = options.w ? options.w : null

        String workingDir =   options.w ? options.w : System.getProperty("user.dir")
        String testfileFullPath = options.f ? options.f : null
        String testFileFullPath = workingDir + "/" + testfileFullPath
        // println "testFileFullPath: " + testFileFullPath

        LinkedHashMap parsedtests = parseTestsYamlFile(testFileFullPath)

        parsedtests.tests.eachWithIndex { testName, i ->

            String testNameOrig = testName

            parsedtests.scripts.each { script ->

                println "-------------------------------------------------------------------------------"
                println " " + (i+1) + "   " + testNameOrig + "    " + script.name + "    " + script.args
                println "-------------------------------------------------------------------------------"

                executeCommand([
                    "groovy boomi.groovy",
                    "-s " + script.name + ".groovy",
                    "-d " + testName + ".dat",
                    "-p " + testName + ".properties",
                    script.args,
                    opt_xd,
                    opt_xp,
                    (options.xp ? "-xp" : ""),
                    "-w " + opt_w

                ].join(" "))

                testName = "_exec/" + script.name.replaceFirst(/.*\//, "") + "_out"
            }

            // println ""
        }
    }

    private static void executeCommand(String command) {
        def os = System.getProperty("os.name")
        if (os.contains("Windows")) {
            command = 'powershell -ExecutionPolicy Bypass -NoLogo -NonInteractive -NoProfile -Command ' + command
            // println command
            def exec = command.execute()
            exec.waitFor()
            println exec.getText()
        }
        else {
            // println command
            def result = new StringBuilder()
            def error = new StringBuilder()
            def exec = command.execute()

            exec.consumeProcessOutput(result, error)
            exec.waitForOrKill(1000)

            if (!error.toString().equals("")) {
                println "ERROR"
                println error
                println result
            } else {
                println "HELLO"
                println result.toString()
            }
        }
    }

    private static LinkedHashMap parseTestsYamlFile(String testfileFullPath) {

        def testfile = new FileInputStream(testfileFullPath)
        def reader = new BufferedReader(new InputStreamReader(testfile))
        def line
        def isTest = false
        def isScript = false
        def tests = []
        def scripts = []

        while ((line = reader.readLine()) != null ) {
            if (line.startsWith("tests:")) {
                isTest = true
                isScript = false
            }
            else if (line.startsWith("scripts:")) {
                isTest = false
                isScript = true
            }
            else if (line != "" && !(line =~ /^\s*#/)){
                if (isTest) {
                    tests << line.replaceFirst(/^\s*-\s*/,"")
                }
                else if (isScript) {
                    def scriptItem = line.replaceFirst(/^\s*-\s*/,"")
                    def scriptItemArr = scriptItem.split(" -", 2)

                    scripts << [
                        name: scriptItemArr[0],
                        args: scriptItemArr.size() > 0 ? " -" + scriptItemArr[1] : null
                    ]
                }
            }
        }

        return [
            tests: tests,
            scripts: scripts
        ]
    }
}
