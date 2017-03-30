package metadata.etl.lhotse;

import akka.actor.UntypedActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by hadoop on 3/30/17.
 */
public class LzLineageExtractorActor extends UntypedActor {
    private static final Logger logger = LoggerFactory.getLogger(LzLineageExtractorActor.class);;
    public LzLineageExtractorActor() {
        super();
    }

    @Override
    public void onReceive(Object message) {
        try {
            if (message instanceof LzExecMessage) {
                logger.debug("LzLineageExtractorActor recieved a message : " + message.toString());
                LzLineageExtractor.extract((LzExecMessage) message);
            } else {
                logger.error("Not an LzExecMessage message");
            }
        } catch (Exception e) {
            logger.error("LzLineageExtractorActor: Actor failed!");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error(sw.toString());
            e.printStackTrace();
        } finally {
            logger.debug("Actor finished for message : " + message.toString());
            getSender().tell("finished", null);
        }
    }
}
