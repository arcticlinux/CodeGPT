package ee.carlrobert.codegpt.toolwindow.chat;

import static ee.carlrobert.codegpt.util.SwingUtils.createIconLabel;
import static ee.carlrobert.codegpt.util.SwingUtils.createTextPane;
import static ee.carlrobert.codegpt.util.SwingUtils.justifyLeft;
import static java.lang.String.format;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import ee.carlrobert.codegpt.state.AccountDetailsState;
import ee.carlrobert.codegpt.state.conversations.Conversation;
import ee.carlrobert.codegpt.state.conversations.ConversationsState;
import ee.carlrobert.codegpt.state.conversations.message.Message;
import ee.carlrobert.codegpt.state.settings.SettingsConfigurable;
import ee.carlrobert.codegpt.state.settings.SettingsState;
import ee.carlrobert.codegpt.client.RequestHandler;
import ee.carlrobert.codegpt.toolwindow.components.GenerateButton;
import ee.carlrobert.codegpt.toolwindow.components.LandingView;
import ee.carlrobert.codegpt.toolwindow.components.ScrollPane;
import ee.carlrobert.codegpt.toolwindow.components.SyntaxTextArea;
import ee.carlrobert.codegpt.toolwindow.components.TextArea;
import icons.Icons;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jetbrains.annotations.NotNull;

public class ChatToolWindowTabPanel {

  private final List<SyntaxTextArea> textAreas = new ArrayList<>();
  private final Project project;
  private JPanel chatGptToolWindowContent;
  private ScrollPane scrollPane;
  private ScrollablePanel scrollablePanel;
  private JTextArea textArea;
  private JScrollPane textAreaScrollPane;
  private GenerateButton generateButton;
  private boolean isLandingViewVisible;
  private Conversation conversation;

  public ChatToolWindowTabPanel(@NotNull Project project) {
    this.project = project;
  }

  public JPanel getContent() {
    return chatGptToolWindowContent;
  }

  public Conversation getConversation() {
    return conversation;
  }

  public void setConversation(Conversation conversation) {
    this.conversation = conversation;
  }

  public void displayUserMessage(String userMessage) {
    addIconLabel(AllIcons.General.User, AccountDetailsState.getInstance().accountName);
    scrollablePanel.add(createTextPane(userMessage));
    scrollablePanel.revalidate();
    scrollablePanel.repaint();
  }

  public void displayLandingView() {
    if (!isLandingViewVisible) {
      SwingUtilities.invokeLater(() -> {
        clearWindow();
        isLandingViewVisible = true;

        addSpacing(16);
        var landingView = new LandingView();
        scrollablePanel.add(landingView.createImageIconPanel());
        addSpacing(16);
        landingView.getQuestionPanels().forEach(panel -> {
          scrollablePanel.add(panel);
          addSpacing(16);
        });
        scrollablePanel.revalidate();
        scrollablePanel.repaint();
      });
    }
  }

  public void displayConversation(Conversation conversation) {
    setConversation(conversation);
    clearWindow();
    conversation.getMessages().forEach(message -> {
      displayUserMessage(message.getPrompt());
      addIconLabel(Icons.DefaultImageIcon, "ChatGPT");
      var textArea = new SyntaxTextArea(true, false, SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
      textArea.setText(message.getResponse());
      textArea.displayCopyButton();
      scrollablePanel.add(textArea);
      textAreas.add(textArea);
    });
    scrollToBottom();
    scrollablePanel.revalidate();
    scrollablePanel.repaint();
  }

  public void sendMessage(String prompt, Project project) {
    sendMessage(prompt, project, false);
  }

  public void sendMessage(String prompt, Project project, boolean isRetry) {
    if (!isRetry) {
      addIconLabel(Icons.DefaultImageIcon, "ChatGPT");
    }

    var settings = SettingsState.getInstance();
    if (settings.apiKey.isEmpty()) {
      notifyMissingCredential(project, "API key not provided.");
    } else {
      SyntaxTextArea textArea;
      if (isRetry) {
        textArea = textAreas.get(textAreas.size() - 1);
        textArea.clear();
      } else {
        textArea = new SyntaxTextArea(true, true, SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        scrollablePanel.add(textArea);
        textAreas.add(textArea);
      }
      call(textArea, prompt, isRetry);
    }
  }

  public void call(SyntaxTextArea textArea, String prompt, boolean isRetry) {
    if (conversation == null) {
      conversation = ConversationsState.getInstance().startConversation();
    }

    var conversationMessage = new Message(prompt);
    var requestService = new RequestHandler(conversation) {
      public void handleMessage(String message) {
        try {
          textArea.append(message);
          conversationMessage.setResponse(textArea.getText());
          scrollToBottom();
        } catch (Exception e) {
          textArea.append("Something went wrong. Please try again later.");
          throw new RuntimeException(e);
        }
      }

      public void handleComplete() {
        stopGenerating(prompt, textArea);
      }

      public void handleError(String errorMessage) {
        textArea.append(errorMessage);
      }
    };
    requestService.call(conversationMessage, isRetry);
    displayGenerateButton(requestService::cancel);
  }

  public void clearWindow() {
    isLandingViewVisible = false;
    generateButton.setVisible(false);
    textAreas.clear();
    scrollablePanel.removeAll();
  }

  public void addSpacing(int height) {
    scrollablePanel.add(Box.createVerticalStrut(height));
  }

  public void addIconLabel(Icon icon, String text) {
    addSpacing(8);
    scrollablePanel.add(justifyLeft(createIconLabel(icon, text)));
    addSpacing(8);
  }

  public void notifyMissingCredential(Project project, String text) {
    var label = new JLabel(format("<html>%s <font color='#589df6'><u>Open Settings</u></font> to set one.</html>", text));
    label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    label.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, SettingsConfigurable.class);
      }
    });
    scrollablePanel.add(justifyLeft(label));
  }

  public void displayGenerateButton(Runnable onClick) {
    generateButton.setVisible(true);
    generateButton.setMode(GenerateButton.Mode.STOP, onClick);
  }

  public void stopGenerating(String prompt, SyntaxTextArea textArea) {
    generateButton.setMode(GenerateButton.Mode.REFRESH, () -> {
      sendMessage(prompt, project, true);
      scrollToBottom();
    });
    textArea.displayCopyButton();
    textArea.getCaret().setVisible(false);
    scrollToBottom();
  }

  public void scrollToBottom() {
    scrollPane.scrollToBottom();
  }

  private void handleSubmit() {
    var searchText = textArea.getText();
    if (isLandingViewVisible) {
      clearWindow();
    }
    if (ConversationsState.getCurrentConversation() == null) {
      setConversation(ConversationsState.getInstance().startConversation());
    }
    displayUserMessage(searchText);
    sendMessage(searchText, project);
    textArea.setText("");
    scrollToBottom();
    scrollablePanel.revalidate();
    scrollablePanel.repaint();
  }

  private void createUIComponents() {
    textAreaScrollPane = new JBScrollPane() {
      public JScrollBar createVerticalScrollBar() {
        JScrollBar verticalScrollBar = new JScrollPane.ScrollBar(1);
        verticalScrollBar.setPreferredSize(new Dimension(0, 0));
        return verticalScrollBar;
      }
    };
    textAreaScrollPane.setBorder(null);
    textAreaScrollPane.setViewportBorder(null);
    textAreaScrollPane.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
        BorderFactory.createEmptyBorder(0, 5, 0, 10)));
    textArea = new TextArea(this::handleSubmit, textAreaScrollPane);
    textAreaScrollPane.setViewportView(textArea);

    scrollablePanel = new ScrollablePanel();
    scrollablePanel.setLayout(new BoxLayout(scrollablePanel, BoxLayout.Y_AXIS));
    scrollPane = new ScrollPane(scrollablePanel);

    generateButton = new GenerateButton();
  }
}
