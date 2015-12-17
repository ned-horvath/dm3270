package com.bytezone.dm3270.assistant;

import com.bytezone.dm3270.application.KeyboardStatusChangedEvent;
import com.bytezone.dm3270.application.KeyboardStatusListener;
import com.bytezone.dm3270.application.Site;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.ScreenChangeListener;
import com.bytezone.dm3270.display.ScreenWatcher;

import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;

public abstract class AbstractTransferTab extends Tab
    implements ScreenChangeListener, KeyboardStatusListener
{
  protected final Screen screen;
  protected final Site site;
  protected final Button btnExecute;
  protected final TextField txtCommand;
  protected ScreenWatcher screenDetails;

  public AbstractTransferTab (String name, Screen screen, Site site, TextField text,
      Button execute)
  {
    super (name);

    setClosable (false);

    this.screen = screen;
    this.site = site;
    this.txtCommand = text;
    this.btnExecute = execute;
  }

  protected void eraseCommand ()
  {
    txtCommand.setText ("");
    btnExecute.setDisable (true);
  }

  abstract protected void setText ();

  protected void setButton ()
  {
    btnExecute.setDisable (screen.isKeyboardLocked () || screenDetails == null
        || screenDetails.getTSOCommandField () == null
        || txtCommand.getText ().isEmpty ());
  }

  @Override
  public void screenChanged (ScreenWatcher screenDetails)
  {
    this.screenDetails = screenDetails;
    if (isSelected () && screenDetails != null)
      setText ();
  }

  @Override
  public void keyboardStatusChanged (KeyboardStatusChangedEvent evt)
  {
    if (isSelected ())
      setButton ();
  }
}