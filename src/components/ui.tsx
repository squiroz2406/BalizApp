import { MaterialCommunityIcons } from '@expo/vector-icons';
import React from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { VehicleType } from '../data/models';
import { useI18n } from '../i18n';
import { useSyncStatus } from '../hooks/useData';
import { useTheme } from '../theme/ThemeProvider';

export type IconName = React.ComponentProps<typeof MaterialCommunityIcons>['name'];

/* ---------- Texto ---------- */
type Variant = 'headline' | 'title' | 'titleMedium' | 'body' | 'label' | 'small';
const typeScale: Record<Variant, TextStyle> = {
  headline: { fontSize: 28, fontWeight: '700' },
  title: { fontSize: 22, fontWeight: '600' },
  titleMedium: { fontSize: 16, fontWeight: '600' },
  body: { fontSize: 15 },
  label: { fontSize: 13, fontWeight: '600' },
  small: { fontSize: 12 },
};

export function Txt({
  variant = 'body',
  color,
  style,
  ...props
}: React.ComponentProps<typeof Text> & { variant?: Variant; color?: string }) {
  const { colors } = useTheme();
  return <Text {...props} style={[typeScale[variant], { color: color ?? colors.onSurface }, style]} />;
}

/* ---------- Botones ---------- */
type ButtonVariant = 'filled' | 'tonal' | 'outlined' | 'text' | 'danger';

export function Button({
  label,
  onPress,
  variant = 'filled',
  disabled,
  loading,
  icon,
  style,
}: {
  label: string;
  onPress?: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
  icon?: IconName;
  style?: StyleProp<ViewStyle>;
}) {
  const { colors } = useTheme();
  const palette = {
    filled: { bg: colors.primary, fg: colors.onPrimary, border: 'transparent' },
    tonal: { bg: colors.primaryContainer, fg: colors.onPrimaryContainer, border: 'transparent' },
    outlined: { bg: 'transparent', fg: colors.primary, border: colors.outline },
    text: { bg: 'transparent', fg: colors.primary, border: 'transparent' },
    danger: { bg: colors.error, fg: colors.onError, border: 'transparent' },
  }[variant];
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={inactive ? undefined : onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: palette.bg, borderColor: palette.border, opacity: inactive ? 0.5 : pressed ? 0.85 : 1 },
        variant === 'text' && { paddingHorizontal: 12 },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator size="small" color={palette.fg} />
      ) : (
        icon && <MaterialCommunityIcons name={icon} size={18} color={palette.fg} />
      )}
      <Txt variant="label" color={palette.fg} style={{ fontSize: 14 }}>
        {label}
      </Txt>
    </Pressable>
  );
}

/* ---------- Campo de texto ---------- */
export function TextField({
  label,
  error,
  style,
  ...props
}: React.ComponentProps<typeof TextInput> & { label: string; error?: boolean }) {
  const { colors } = useTheme();
  return (
    <View style={[{ gap: 4 }, style as StyleProp<ViewStyle>]}>
      <Txt variant="label" color={error ? colors.error : colors.onSurfaceVariant}>
        {label}
      </Txt>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={colors.outline}
        {...props}
        style={[
          styles.input,
          {
            color: colors.onSurface,
            borderColor: error ? colors.error : colors.outline,
            backgroundColor: colors.surface,
          },
          props.multiline && { minHeight: 80, textAlignVertical: 'top' },
        ]}
      />
    </View>
  );
}

/* ---------- Chips, tarjetas, secciones ---------- */
export function Chip({
  label,
  selected,
  onPress,
  icon,
}: {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  icon?: IconName;
}) {
  const { colors } = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: !!selected }}
      onPress={onPress}
      style={[
        styles.chip,
        {
          backgroundColor: selected ? colors.secondaryContainer : 'transparent',
          borderColor: selected ? colors.secondaryContainer : colors.outline,
        },
      ]}
    >
      {icon && <MaterialCommunityIcons name={icon} size={16} color={colors.onSecondaryContainer} />}
      <Txt variant="label" color={selected ? colors.onSecondaryContainer : colors.onSurfaceVariant}>
        {label}
      </Txt>
    </Pressable>
  );
}

export function Card({
  children,
  style,
  onPress,
  tone = 'surface',
}: {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  onPress?: () => void;
  tone?: 'surface' | 'primary' | 'error' | 'success';
}) {
  const { colors } = useTheme();
  const bg = {
    surface: colors.surfaceVariant,
    primary: colors.primaryContainer,
    error: colors.errorContainer,
    success: colors.successContainer,
  }[tone];
  const body = <View style={[styles.card, { backgroundColor: bg }, style]}>{children}</View>;
  return onPress ? (
    <Pressable accessibilityRole="button" onPress={onPress}>
      {body}
    </Pressable>
  ) : (
    body
  );
}

export function SectionTitle({ children }: { children: string }) {
  return (
    <Txt variant="titleMedium" style={{ marginTop: 8 }}>
      {children}
    </Txt>
  );
}

export function ErrorText({ children }: { children?: string | null }) {
  const { colors } = useTheme();
  if (!children) return null;
  return (
    <Txt variant="small" color={colors.error} accessibilityRole="alert" style={{ fontSize: 13 }}>
      {children}
    </Txt>
  );
}

export function VehicleIcon({ type, size = 24, color }: { type: VehicleType; size?: number; color?: string }) {
  const { colors } = useTheme();
  return (
    <MaterialCommunityIcons
      name={type === 'motorcycle' ? 'motorbike' : 'car'}
      size={size}
      color={color ?? colors.primary}
    />
  );
}

export function EmptyState({ icon, title, text }: { icon: IconName; title: string; text?: string }) {
  const { colors } = useTheme();
  return (
    <View style={styles.empty}>
      <MaterialCommunityIcons name={icon} size={48} color={colors.outline} />
      <Txt variant="titleMedium" style={{ textAlign: 'center' }}>
        {title}
      </Txt>
      {text ? (
        <Txt color={colors.onSurfaceVariant} style={{ textAlign: 'center' }}>
          {text}
        </Txt>
      ) : null}
    </View>
  );
}

/* ---------- Pantalla con encabezado ---------- */
export function Screen({
  title,
  onBack,
  children,
  scroll = true,
  padded = true,
  right,
}: {
  title?: string;
  onBack?: () => void;
  children: React.ReactNode;
  scroll?: boolean;
  padded?: boolean;
  right?: React.ReactNode;
}) {
  const { colors } = useTheme();
  const { t } = useI18n();
  const content = scroll ? (
    <ScrollView
      contentContainerStyle={[padded && styles.screenPad, { gap: 12 }]}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  ) : (
    <View style={[{ flex: 1, gap: 12 }, padded && styles.screenPad]}>{children}</View>
  );
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }} edges={['top', 'left', 'right']}>
      {title ? (
        <View style={styles.header}>
          {onBack && (
            <Pressable accessibilityRole="button" accessibilityLabel={t('core_back')} onPress={onBack} hitSlop={12}>
              <MaterialCommunityIcons name="arrow-left" size={26} color={colors.onSurface} />
            </Pressable>
          )}
          <Txt variant="title" style={{ flex: 1 }} numberOfLines={1}>
            {title}
          </Txt>
          {right}
        </View>
      ) : null}
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        {content}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

/* ---------- Diálogo ---------- */
export function Dialog({
  visible,
  title,
  text,
  confirmLabel,
  dismissLabel,
  onConfirm,
  onDismiss,
  destructive,
  children,
}: {
  visible: boolean;
  title: string;
  text?: string;
  confirmLabel: string;
  dismissLabel?: string;
  onConfirm: () => void;
  onDismiss: () => void;
  destructive?: boolean;
  children?: React.ReactNode;
}) {
  const { colors } = useTheme();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onDismiss}>
      <View style={styles.backdrop}>
        <View style={[styles.dialog, { backgroundColor: colors.background }]}>
          <Txt variant="title">{title}</Txt>
          {text ? <Txt color={colors.onSurfaceVariant}>{text}</Txt> : null}
          {children}
          <View style={styles.dialogActions}>
            {dismissLabel ? <Button label={dismissLabel} variant="text" onPress={onDismiss} /> : null}
            <Button label={confirmLabel} variant={destructive ? 'danger' : 'filled'} onPress={onConfirm} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

/* ---------- Selector segmentado ---------- */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  const { colors } = useTheme();
  return (
    <View style={[styles.segmented, { borderColor: colors.outline }]}>
      {options.map((o) => (
        <Pressable
          key={o.value}
          accessibilityRole="button"
          accessibilityState={{ selected: o.value === value }}
          onPress={() => onChange(o.value)}
          style={[styles.segment, o.value === value && { backgroundColor: colors.secondaryContainer }]}
        >
          <Txt variant="label" color={o.value === value ? colors.onSecondaryContainer : colors.onSurface}>
            {o.label}
          </Txt>
        </Pressable>
      ))}
    </View>
  );
}

/* ---------- Franja de conexión ---------- */
/** Avisa si no hay conexión o si hay cambios por sincronizar. */
export function ConnectionBanner() {
  const status = useSyncStatus();
  const { colors } = useTheme();
  const { t } = useI18n();
  if (status === 'online') return null;
  const offline = status === 'offline';
  return (
    <View style={{ backgroundColor: offline ? colors.errorContainer : colors.secondaryContainer, paddingVertical: 6, paddingHorizontal: 16 }}>
      <Txt variant="small" color={offline ? colors.onErrorContainer : colors.onSecondaryContainer} style={{ textAlign: 'center' }}>
        {offline ? t('map_banner_offline') : t('map_banner_syncing')}
      </Txt>
    </View>
  );
}

const styles = StyleSheet.create({
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    minHeight: 44,
    paddingHorizontal: 20,
    borderRadius: 22,
    borderWidth: 1,
  },
  input: { borderWidth: 1, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, fontSize: 16 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    minHeight: 34,
    borderRadius: 8,
    borderWidth: 1,
  },
  card: { borderRadius: 16, padding: 16, gap: 4 },
  empty: { alignItems: 'center', gap: 8, padding: 32 },
  screenPad: { padding: 16 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 12 },
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 24 },
  dialog: { borderRadius: 24, padding: 24, gap: 12 },
  dialogActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8, marginTop: 8 },
  segmented: { flexDirection: 'row', borderWidth: 1, borderRadius: 20, overflow: 'hidden' },
  segment: { flex: 1, alignItems: 'center', justifyContent: 'center', minHeight: 40 },
});
