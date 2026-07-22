package dev.freitas.delve.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

import dev.freitas.delve.discord.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LoadModuleCommandTest {

    private LoadModuleCommand command;
    private CommandContext ctx;

    @BeforeEach
    void setUp() {
        command = new LoadModuleCommand();
        ctx = mock(CommandContext.class);
        Mockito.when(ctx.getPrefix()).thenReturn("!");
    }

    @Test
    void listsModulesWhenNoArgumentProvided() {
        Mockito.when(ctx.getArgumentText()).thenReturn("");

        command.invoke(ctx);

        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(ctx).reply(replyCaptor.capture());
        String reply = replyCaptor.getValue();

        assertThat(reply).contains("**Available modules**");
        assertThat(reply).contains("sample");
    }

    @Test
    void inspectsModuleDetailsWhenSampleProvided() {
        Mockito.when(ctx.getArgumentText()).thenReturn("sample");

        command.invoke(ctx);

        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(ctx).reply(replyCaptor.capture());
        String reply = replyCaptor.getValue();

        assertThat(reply).contains("`sample`");
        assertThat(reply).contains("Levels:");
        assertThat(reply).contains("Total Rooms:");
    }

    @Test
    void handlesUnknownModuleGracefully() {
        Mockito.when(ctx.getArgumentText()).thenReturn("nonexistent_module_123");

        command.invoke(ctx);

        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(ctx).reply(replyCaptor.capture());
        String reply = replyCaptor.getValue();

        assertThat(reply).contains("No module named **nonexistent_module_123**");
    }
}
