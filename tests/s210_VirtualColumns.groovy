import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def tableInstances = new JsonSlurper().parse(is).Records
    // println prettyJson(tableInstances)
    def virtualColumnsJson = props.getProperty("document.dynamic.userdefined.ddp_VirtualColumns")
    def virtualColumns = virtualColumnsJson ? new JsonSlurper().parseText(virtualColumnsJson).Records[0] : []
    // println prettyJson(virtualColumns)
    def virtualColumnRows = virtualColumns.VirtualColumnRows
    // println prettyJson(virtualColumnRows)

    def outData = tableInstances.collect { ti ->
        [
            "TableInstanceId": ti.TableInstanceId,
            "TableIdentifier": ti.UserInputsIdentifier,
            "Value": virtualColumnRows.find { vc -> ti.TableInstanceId == vc.TableInstanceId }?.Value ?: ""
        ]
    }

    is = new ByteArrayInputStream(prettyJson(outData).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
