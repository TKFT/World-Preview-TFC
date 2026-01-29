package caeruleustait.world.preview.client.gui.widgets;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class SelectionSlider<T extends SelectionSlider.SelectionValues> extends AbstractSliderButton {
   private final List<T> values;
   private final Consumer<T> onValueChange;
   private T currentValue;

   public SelectionSlider(int x, int y, int width, int height, List<T> values, T initialValue, Consumer<T> onValueChange) {
      super(x, y, width, height, initialValue.message(), 0.0);
      this.values = values;
      this.onValueChange = onValueChange;
      this.setValue(initialValue);
   }

   public void setValue(T newValue) {
      if (Objects.equals(this.currentValue, newValue)) {
         this.onValueChange.accept(newValue);
      }

      this.currentValue = newValue;
      this.value = (double)this.values.indexOf(newValue) / (this.values.size() - 1);
      this.updateMessage();
   }

   public T value() {
      return this.currentValue;
   }

   protected void updateMessage() {
      this.setMessage(this.currentValue.message());
   }

   protected void applyValue() {
      T oldValue = this.currentValue;
      this.currentValue = this.values.get((int)(this.value * (this.values.size() - 1)));
      if (!Objects.equals(oldValue, this.currentValue)) {
         this.onValueChange.accept(this.currentValue);
      }
   }

   public interface SelectionValues {
      Component message();
   }
}
