/*
 * openTCS copyright information:
 * Copyright (c) 2013 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */

package org.opentcs.guing.application.action.view;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.opentcs.guing.application.OpenTCSView;

/**
 * An action for adding new transport order views.
 * 
 * @author Philipp Seifert (Fraunhofer IML)
 */
public class AddTransportOrderView extends AbstractAction {
  
  public final static String ID = "view.addTOView";
  private final OpenTCSView view;
  
  public AddTransportOrderView(OpenTCSView view) {
    this.view = view;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    view.addTransportOrderView(true);
  }  
}
