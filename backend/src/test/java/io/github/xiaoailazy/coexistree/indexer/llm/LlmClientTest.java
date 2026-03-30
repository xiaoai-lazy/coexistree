package io.github.xiaoailazy.coexistree.indexer.llm;

import com.volcengine.ark.runtime.model.responses.content.OutputContentItemText;
import com.volcengine.ark.runtime.model.responses.item.BaseItem;
import com.volcengine.ark.runtime.model.responses.item.ItemOutputMessage;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import io.github.xiaoailazy.coexistree.config.LlmProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientTest {

    @Test
    void shouldExtractPlainTextFromArkResponse() {
        LlmClient client = new LlmClient(new LlmProperties("demo-key", "demo-model", "https://ark.cn-beijing.volces.com/api/v3"));

        OutputContentItemText text1 = new OutputContentItemText();
        text1.setText("first line");
        OutputContentItemText text2 = new OutputContentItemText();
        text2.setText("second line");

        ItemOutputMessage message = new ItemOutputMessage();
        message.setContent(List.of(text1, text2));

        ResponseObject response = new ResponseObject();
        response.setOutput(List.<BaseItem>of(message));

        assertThat(client.extractText(response)).isEqualTo("first line\nsecond line");
    }
}
