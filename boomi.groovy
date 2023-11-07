import java.util.logging.Logger;

class BoomiTestBed {
    static void main(String[] args) throws Exception {
        def cli = new CliBuilder(usage: 'BoomiTestBed.groovy [-h][-on][-of][-od][-f][-xd][-xp] -s script [-d document] [-p properties] [-e extension] [-w working-dir] [-rp pattern]')

        cli.with {
            h  longOpt: 'help', 'Show usage'
            s  longOpt: 'script', args: 1, argName: 'referencedFileFullPath', 'Name of the script'
            d  longOpt: 'document', args: 1, argName: 'referencedFileFullPath', 'Name of input document'
            p  longOpt: 'properties', args: 1, argName: 'referencedFileFullPath', 'Name of input properties'
            o  longOpt: 'output-files', type: boolean, 'Output files inside _exec directory'
            xd longOpt: 'suppress-data-output', type: boolean, 'Suppresses output of data'
            xp longOpt: 'suppress-props-output', type: boolean, 'Suppresses output of props'
            rp longOpt: 'ddp-regex-remove', args: 1, argName: 'regex', 'Prevent props from appearing'
            w  longOpt: 'working-dir', args: 1, argName: 'dir', 'Present Working Directory'
        }

        def options = cli.parse(args)

        if (options.h) {
            cli.usage()
            return
        }

        String workingDir = options.w ? options.w : System.getProperty("user.dir")
        String scriptName = options.s ? workingDir + "/" + options.s : null
        String dataDocumentName = options.d ? workingDir + "/" + options.d : null
        String propertiesFileName = options.p ? workingDir + "/" + options.p : null
        // println "PWD: " + System.getProperty("user.dir")
        // println "options.w: " + options.w
        // println "workingDir: " + workingDir
        // println "scriptName: " + scriptName
        // println "dataDocumentName: " + dataDocumentName
        // println "propertiesFileName: " + propertiesFileName

        if (scriptName == null) {
            cli.usage()
            return
        }

        try {
            println new ScriptRunner().run(scriptName, dataDocumentName, propertiesFileName, options)
        }
        catch(Exception e) {
            println("!!! EXCEPTION !!!")
            org.codehaus.groovy.runtime.StackTraceUtils.sanitize(e).printStackTrace()
            System.exit(1)
        }

    }
}

class ScriptRunner {

    String run(String scriptName, String dataDocumentName, String propertiesFileName, def options) {

        // OPTIONS
        Boolean outToFile = options.o
        Boolean suppressData = options.xd
        Boolean suppressProps = options.xp
        String ddpPreplacePattern = options.rp ? options.rp : null
        String os = System.getProperty("os.name")
        String pathDelimiter = os.contains("Windows")? "\\\\" : "/"


        // SCRIPT
        String script = new FileInputStream(scriptName).text -~ /import com[.]boomi[.]execution[.]ExecutionUtil;?/

        // DOCUMENT
        InputStream documentContents = new ByteArrayInputStream("".getBytes("UTF-8"))
        if (dataDocumentName != null) {
            documentContents = new FileInputStream(dataDocumentName)
        }

        // PROPS
        Properties properties = new Properties()
        if (propertiesFileName != null) {
            properties.load(new FileInputStream(propertiesFileName) as InputStream)
        }

        Properties dynamicProcessProperties = new Properties()
        Enumeration<?> e = properties.propertyNames()
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement()
            String value = properties.getProperty(key)

            // within the .properties file, you can set the value of a prop to reference a file
            // - this is usefull if you're storing a large multiline string in a prop 
            //   like an xml or json config
            // - syntax: propName=@file('filepath/filename')
            def referencedFilePath = (value =~ /(?i)^@file\(["'](.*?)["']\)/)
            if (referencedFilePath.size() > 0) {
                // assume the base path of the referenced file is the path of the .properties file
                ArrayList propertiessFileNameArr = propertiesFileName.split(pathDelimiter)
                def propertiesFilePath = propertiessFileNameArr[0..-2].join("/")

                // get content of referenced file
                String referencedFileFullPath = "${propertiesFilePath}/${referencedFilePath[0][1]}"
                value = new FileInputStream(referencedFileFullPath).text.replaceAll("\r?\n","")
                properties.setProperty(key,value)
                // println value
            }
            else {
                // handle "\\" for escaping, e.g. "\\n"
                value = value.replaceAll("\\\\","\\\\\\\\")
            }

            // props that don't start with document.dynamic.userdefined"
            // are assumed to by dynamic process props,
            // so put them into a saparate properties object
            if (!key.startsWith("document.dynamic.userdefined")) {
                dynamicProcessProperties.setProperty(key, value)
                properties.remove(key)
            }
        }

        // EVAL
        DataContext dataContext = new DataContext(documentContents, properties)
        ExecutionUtilHelper ExecutionUtil = new ExecutionUtilHelper()
        ExecutionUtil.dynamicProcessProperties = dynamicProcessProperties;

        Eval.xy(dataContext, ExecutionUtil, "def dataContext = x; def ExecutionUtil = y;" + "${script}; return dataContext")

        def resultString = dataContext.print()
        def dynamicProcessPropsString = formatProps(dynamicProcessProperties)
        def dynamicDocumentPropsString = formatProps(properties)


        // WRITE FILES
        if (outToFile) {

            // separate script name into parts
            ArrayList scriptNameArr = scriptName.split(pathDelimiter)
            def scriptPath = scriptNameArr[0..-2].join("/")
            def scriptNameHead = scriptNameArr[-1].replaceFirst("\\.\\\\","") -~ /\.b\.groovy$/ -~ /\.groovy$/
            println "scriptNameHead: " + scriptNameHead

            // path of dir to write files
            def execFilesPath = scriptPath + "/_exec/"

            // create dir if doesn't exist
            File execFilesDir = new File(execFilesPath);
            if (execFilesDir.exists()) execFilesDir.deleteDir()
            execFilesDir.mkdir()

            // write data file
            File outDataFile = new File(execFilesPath + scriptNameHead + "_out.dat")
            outDataFile.write resultString

            // write props file
            File outPropsFile = new File(execFilesPath + scriptNameHead + "_out.properties")
            outPropsFile.write dynamicProcessPropsString + "\n" + dynamicDocumentPropsString
        }


        // OUTPUT
        def output = ""

        if (!suppressData) {
            output += resultString
        }

        if (!suppressProps) {
            if (properties.propertyNames().hasMoreElements()) {
                output += "\n\nDynamic Document Props\n----------------------\n"
                output += dynamicDocumentPropsString
                    .replaceAll("document.dynamic.userdefined.","")
                    .replaceAll(/($ddpPreplacePattern?)=.*\n/,"\$1=...\n")
            }
            if (dynamicProcessProperties.propertyNames().hasMoreElements()) {
                output += "\n\nDYNAMIC PROCESS PROPS\n---------------------\n"
                output += dynamicProcessPropsString
            }
        }

        return output
    }

    private String formatProps (Properties properties) {
        String propsString = ""
        Enumeration<?> e = properties.propertyNames()
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement()
            String value = properties.getProperty(key)
                                .replaceAll("\r?\n","")
                                .replaceAll("\\\\","\\\\\\\\")
            propsString += "${key}=${value}\n"
        }
        return propsString
    }

}


class ExecutionUtilHelper {
    static def dynamicProcessProperties = new Properties();

    static void setDynamicProcessProperty(String key, String value, boolean persist) {
        dynamicProcessProperties.setProperty(key, value)
    }

    static def getDynamicProcessProperty(String key) {
        return dynamicProcessProperties.getProperty(key)
    }

    static Logger getBaseLogger() {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5\$s %n")
        return Logger.getAnonymousLogger()
    }
}



class DataContext {
    InputStream is
    Properties props

    DataContext(InputStream inputStream, Properties properties){
        is = inputStream
        props = properties
    }

    void storeStream(InputStream inputStream, Properties properties){
        is = inputStream
        props = properties
    }

    int getDataCount(){
        1
    }

    InputStream getStream(int index){
        is
    }

    Properties getProperties(int index) {
        props
    }

    String print() {
      this.is.text
    }
}
